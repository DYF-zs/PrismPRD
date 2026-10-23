package com.prismprd.model;

import com.prismprd.security.PrismPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ModelScopeFilter extends OncePerRequestFilter {
    private final ModelClientRegistry registry;
    public ModelScopeFilter(ModelClientRegistry registry) { this.registry = registry; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PrismPrincipal principal) {
            try (var ignored = ModelScope.open(() -> registry.resolve(principal.userId()))) {
                chain.doFilter(request,response);
            }
        } else chain.doFilter(request,response);
    }
}

