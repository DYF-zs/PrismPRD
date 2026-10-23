package com.prismprd.prd.version;

import com.prismprd.common.BizException;
import com.prismprd.common.ConflictException;
import com.prismprd.common.NotFoundException;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.patch.PrdPatchMapper;
import com.prismprd.prd.patch.PatchService;
import com.prismprd.security.CurrentUserService;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.VersionDetail;
import com.prismprd.web.dto.VersionSummary;
import com.prismprd.web.dto.VersionDiffDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final PrdVersionMapper prdVersionMapper;
    private final PrdDocumentMapper prdDocumentMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final PrdPatchMapper patchMapper;

    @Transactional
    public VersionDetail createVersion(UUID prdId, String changeSummary) {
        UUID userId = currentUserService.currentUserId();
        permissionService.requireEdit(prdId, userId);
        prdDocumentMapper.lockById(prdId);
        PrdDocument document = prdDocumentMapper.selectById(prdId);
        if (document == null) {
            throw new NotFoundException("PRD 不存在");
        }

        PrdVersion version = new PrdVersion();
        version.setId(UUID.randomUUID());
        version.setPrdId(prdId);
        version.setTitleSnapshot(document.getTitle());
        version.setEditorJson(document.getDraftJson());
        version.setMarkdownSnapshot(document.getDraftMarkdown());
        var patches = patchMapper.selectUnversionedApplied(prdId, document.getRevision());
        version.setDraftRevision(document.getRevision());
        version.setSource(patches.isEmpty() ? "HUMAN" : "AI_ASSISTED");
        version.setChangeSummary(changeSummary == null ? "" : changeSummary.trim());
        version.setCreatedBy(userId);
        try {
            prdVersionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("版本创建冲突，请重试");
        }
        prdDocumentMapper.updateCurrentVersion(prdId, version.getId());
        patches.forEach(patch -> patchMapper.linkVersion(version.getId(), patch.getId()));
        return toDetail(prdVersionMapper.selectById(version.getId()));
    }

    public List<VersionSummary> listVersions(UUID prdId) {
        UUID userId = currentUserService.currentUserId();
        permissionService.requireView(prdId, userId);
        return prdVersionMapper.selectByPrd(prdId).stream()
                .map(v -> new VersionSummary(v.getId(), v.getPrdId(), v.getVersionNo(),
                        v.getSource(), v.getChangeSummary(), v.getCreatedBy(), v.getCreatedAt()))
                .toList();
    }

    public VersionDetail getVersion(UUID versionId) {
        UUID userId = currentUserService.currentUserId();
        PrdVersion version = requireVersion(versionId);
        permissionService.requireView(version.getPrdId(), userId);
        return toDetail(version);
    }

    public VersionDiffDetail compareVersions(UUID leftId, UUID rightId) {
        UUID userId = currentUserService.currentUserId();
        PrdVersion left = requireVersion(leftId), right = requireVersion(rightId);
        permissionService.requireView(left.getPrdId(), userId);
        permissionService.requireView(right.getPrdId(), userId);
        if (!left.getPrdId().equals(right.getPrdId())) throw new BizException("请选择同一 PRD 的两个版本");
        var lines = VersionTextDiff.compare(left.getMarkdownSnapshot(), right.getMarkdownSnapshot());
        return new VersionDiffDetail(leftId, rightId, left.getVersionNo(), right.getVersionNo(),
                lines.stream().filter(l -> l.type().equals("INSERT")).count(),
                lines.stream().filter(l -> l.type().equals("DELETE")).count(), lines,
                patchMapper.selectByVersion(rightId).stream().map(PatchService::toDetail).toList());
    }

    private PrdVersion requireVersion(UUID versionId) {
        PrdVersion version = prdVersionMapper.selectById(versionId);
        if (version == null) {
            throw new NotFoundException("版本不存在");
        }
        return version;
    }

    private VersionDetail toDetail(PrdVersion version) {
        return new VersionDetail(version.getId(), version.getPrdId(), version.getVersionNo(),
                version.getEditorJson(), version.getMarkdownSnapshot(), version.getSource(),
                version.getChangeSummary(), version.getCreatedBy(), version.getCreatedAt(),
                version.getDraftRevision(), patchMapper.selectByVersion(version.getId()).stream()
                        .map(PatchService::toDetail).toList());
    }
}
