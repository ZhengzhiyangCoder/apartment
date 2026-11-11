package com.example.apartment.modules.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final IdentityService identity;
    public IdentityController(IdentityService identity) { this.identity = identity; }

    @GetMapping("/api/auth/csrf")
    public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getHeaderName(), token.getToken()); }

    @GetMapping("/api/auth/me")
    public ProfileView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return identity.profile(principal);
    }

    @PostMapping("/api/auth/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest input, HttpServletRequest request) {
        identity.changePassword(principal, input);
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/identity/workspaces/{role}")
    public WorkspaceView workspace(@PathVariable Role role, @AuthenticationPrincipal AccountPrincipal principal) {
        if (role != principal.role()) throw new AccessDeniedException("无权访问该角色工作台");
        return new WorkspaceView(role, role.label() + "工作台");
    }

    public record CsrfView(String headerName, String token) { }
    public record WorkspaceView(Role role, String title) { }
}
