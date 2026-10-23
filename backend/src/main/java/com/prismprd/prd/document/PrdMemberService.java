package com.prismprd.prd.document;

import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.security.AppUser;
import com.prismprd.security.AppUserMapper;
import com.prismprd.security.CurrentUserService;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.PrdMembersResponse;
import com.prismprd.web.dto.SetMembersRequest;
import com.prismprd.web.dto.TransferOwnerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrdMemberService {

    private static final Set<String> MEMBER_ROLES = Set.of("EDITOR", "REVIEWER", "VIEWER");

    private final PrdDocumentMapper prdDocumentMapper;
    private final PrdMemberMapper prdMemberMapper;
    private final AppUserMapper appUserMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public PrdMembersResponse listMembers(UUID prdId) {
        UUID userId = currentUserService.currentUserId();
        permissionService.requireView(prdId, userId);
        PrdDocument document = requireDocument(prdId);
        AppUser owner = appUserMapper.selectById(document.getOwnerId());
        PrdMembersResponse.MemberView ownerView = new PrdMembersResponse.MemberView(
                owner.getId(), "OWNER", owner.getUsername(), owner.getDisplayName(), owner.getEmail());
        List<PrdMembersResponse.MemberView> members = prdMemberMapper.selectByPrd(prdId).stream()
                .map(m -> new PrdMembersResponse.MemberView(
                        m.getUserId(), m.getRole(), m.getUsername(), m.getDisplayName(), m.getEmail()))
                .toList();
        return new PrdMembersResponse(ownerView, members);
    }

    @Transactional
    public PrdMembersResponse setMembers(UUID prdId, SetMembersRequest request) {
        UUID userId = currentUserService.currentUserId();
        prdDocumentMapper.lockById(prdId);
        permissionService.requireManage(prdId, userId);
        PrdDocument document = requireDocument(prdId);

        List<SetMembersRequest.MemberInput> inputs = request.members() == null ? List.of() : request.members();
        Set<UUID> distinctUsers = new HashSet<>();
        for (SetMembersRequest.MemberInput input : inputs) {
            if (input == null || input.userId() == null) {
                throw new BizException("成员 userId 不能为空");
            }
            if (input.role() == null || !MEMBER_ROLES.contains(input.role())) {
                throw new BizException("非法成员角色：" + input.role());
            }
            if (!distinctUsers.add(input.userId())) {
                throw new BizException("成员列表存在重复用户：" + input.userId());
            }
        }

        List<SetMembersRequest.MemberInput> effective = inputs.stream()
                .filter(input -> !input.userId().equals(document.getOwnerId()))
                .toList();

        if (!effective.isEmpty()) {
            List<AppUser> users = appUserMapper.selectByIds(
                    effective.stream().map(SetMembersRequest.MemberInput::userId).toList());
            if (users.size() != effective.size() || users.stream().anyMatch(u -> !Boolean.TRUE.equals(u.getEnabled()))) {
                throw new NotFoundException("部分成员用户不存在或已禁用");
            }
        }
        prdMemberMapper.deleteByPrd(prdId);
        for (SetMembersRequest.MemberInput input : effective) {
            PrdMember member = new PrdMember();
            member.setId(UUID.randomUUID());
            member.setPrdId(prdId);
            member.setUserId(input.userId());
            member.setRole(input.role());
            member.setCreatedBy(userId);
            prdMemberMapper.insert(member);
        }

        return listMembers(prdId);
    }

    @Transactional
    public PrdMembersResponse transferOwner(UUID prdId, TransferOwnerRequest request) {
        UUID userId = currentUserService.currentUserId();
        prdDocumentMapper.lockById(prdId);
        permissionService.requireManage(prdId, userId);
        PrdDocument document = requireDocument(prdId);
        UUID targetUserId = request.userId();

        if (targetUserId.equals(document.getOwnerId())) {
            throw new BizException("目标用户已是 Owner");
        }
        AppUser target = appUserMapper.selectById(targetUserId);
        if (target == null || !Boolean.TRUE.equals(target.getEnabled())) {
            throw new NotFoundException("目标用户不存在或已禁用");
        }

        prdDocumentMapper.updateOwner(prdId, targetUserId);

        if (prdMemberMapper.selectRole(prdId, userId) == null) {
            PrdMember previousOwner = new PrdMember();
            previousOwner.setId(UUID.randomUUID());
            previousOwner.setPrdId(prdId);
            previousOwner.setUserId(userId);
            previousOwner.setRole("EDITOR");
            previousOwner.setCreatedBy(userId);
            prdMemberMapper.insert(previousOwner);
        }
        return listMembers(prdId);
    }

    private PrdDocument requireDocument(UUID prdId) {
        PrdDocument document = prdDocumentMapper.selectById(prdId);
        if (document == null) {
            throw new NotFoundException("PRD 不存在");
        }
        return document;
    }
}
