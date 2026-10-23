package com.prismprd.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class DevAuthFilter extends OncePerRequestFilter {

    private final PrismAuthProperties.DevUser devUser;
    private final AppUserMapper appUserMapper;
    private volatile UUID userId;

    public DevAuthFilter(PrismAuthProperties.DevUser devUser, AppUserMapper appUserMapper) {
        this.devUser = devUser;
        this.appUserMapper = appUserMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null || !existing.isAuthenticated()) {
            PrismPrincipal principal = new PrismPrincipal(resolveUserId(), devUser.getUsername(),
                    devUser.getDisplayName(), "DEV");
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, AuthorityUtils.NO_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private UUID resolveUserId() {
        if (userId != null) {
            return userId;
        }
        synchronized (this) {
            if (userId == null) {
                AppUser user = appUserMapper.selectByDevSubject(devUser.getSubject());
                if (user == null) {
                    user = new AppUser();
                    user.setId(UUID.randomUUID());
                    user.setAuthSource("DEV");
                    user.setSubject(devUser.getSubject());
                    user.setUsername(devUser.getUsername());
                    user.setDisplayName(devUser.getDisplayName());
                    user.setEmail(devUser.getEmail());
                    user.setEnabled(true);
                    try {
                        appUserMapper.insert(user);
                    } catch (Exception e) {
                        user = appUserMapper.selectByDevSubject(devUser.getSubject());
                        if (user == null) {
                            throw new IllegalStateException("dev 用户初始化失败", e);
                        }
                    }
                }
                userId = user.getId();
            }
            return userId;
        }
    }
}
