package com.example.apartment.modules.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/identity/accounts")
public class AccountAdministrationController {
    private final AccountAdministrationService service;
    public AccountAdministrationController(AccountAdministrationService service) { this.service = service; }

    @GetMapping
    public AccountPage list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(q, role, enabled, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody AccountRequests.Create input) {
        return service.create(actor, input);
    }

    @PostMapping("/{id}/role")
    public AccountView changeRole(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id,
            @Valid @RequestBody AccountRequests.ChangeRole input) {
        return service.changeRole(actor, id, input);
    }

    @PostMapping("/{id}/enabled")
    public AccountView changeEnabled(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id,
            @Valid @RequestBody AccountRequests.ChangeEnabled input) {
        return service.changeEnabled(actor, id, input);
    }

    @PostMapping("/{id}/reset-password")
    public AccountView resetPassword(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id,
            @Valid @RequestBody AccountRequests.ResetPassword input) {
        return service.resetPassword(actor, id, input);
    }
}
