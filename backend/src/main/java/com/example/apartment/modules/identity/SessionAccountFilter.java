package com.example.apartment.modules.identity;

import com.example.apartment.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SessionAccountFilter extends OncePerRequestFilter {
    private final IdentityService identity;
    private final ObjectMapper json;
    public SessionAccountFilter(IdentityService identity, ObjectMapper json) {
        this.identity = identity; this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            boolean valid;
            try {
                valid = identity.isSessionValid(principal);
            } catch (DataAccessException failure) {
                logger.error("Unable to validate current account", failure);
                ApiError.write(json, response, 500, "SERVER_ERROR", "服务暂时不可用，请稍后重试");
                return;
            }
            if (!valid) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                ApiError.write(json, response, 401, "UNAUTHENTICATED", "登录状态已失效，请重新登录");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
