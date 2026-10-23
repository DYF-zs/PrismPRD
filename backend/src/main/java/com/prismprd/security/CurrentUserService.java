package com.prismprd.security;

import com.prismprd.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserMapper appUserMapper;

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PrismPrincipal principal) {
            return principal.userId();
        }
        throw new BizException(401, "未认证或认证主体不合法");
    }

    public AppUser currentUser() {
        AppUser user = appUserMapper.selectById(currentUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(401, "当前用户不存在或已禁用");
        }
        return user;
    }
}
