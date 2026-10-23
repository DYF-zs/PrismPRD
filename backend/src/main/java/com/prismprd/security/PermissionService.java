package com.prismprd.security;

import com.prismprd.common.NotFoundException;
import com.prismprd.common.PermissionDeniedException;
import com.prismprd.prd.document.PrdDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PrdDocumentMapper prdDocumentMapper;

    public PrdRole resolveRole(UUID prdId, UUID userId) {
        String role = prdDocumentMapper.resolveUserRole(prdId, userId);
        return role == null ? null : PrdRole.valueOf(role);
    }

    public PrdRole requireView(UUID prdId, UUID userId) {
        PrdRole role = resolveRole(prdId, userId);
        if (role == null) {
            throw new NotFoundException("PRD 不存在或无权访问");
        }
        return role;
    }

    public void requireEdit(UUID prdId, UUID userId) {
        PrdRole role = requireView(prdId, userId);
        if (!role.canEdit()) {
            throw new PermissionDeniedException("需要 Editor 或 Owner 权限");
        }
    }

    public void requireReview(UUID prdId, UUID userId) {
        PrdRole role = requireView(prdId, userId);
        if (!role.canReview()) {
            throw new PermissionDeniedException("需要 Reviewer 及以上权限");
        }
    }

    public void requireManage(UUID prdId, UUID userId) {
        PrdRole role = requireView(prdId, userId);
        if (!role.canManage()) {
            throw new PermissionDeniedException("需要 Owner 权限");
        }
    }
}
