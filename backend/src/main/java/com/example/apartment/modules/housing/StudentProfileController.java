package com.example.apartment.modules.housing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.apartment.modules.identity.AccountPrincipal;

@RestController
@RequestMapping("/api/housing/students")
public class StudentProfileController {
    private final StudentProfileService service;
    public StudentProfileController(StudentProfileService service) { this.service = service; }
    @GetMapping
    public HousingPage<StudentView> list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.list(q, enabled, page, size);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StudentView create(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody StudentRequests.Create input) {
        return service.create(actor, input);
    }
    @PostMapping("/{id}")
    public StudentView update(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody StudentRequests.Update input) {
        return service.update(actor, id, input);
    }
    @PostMapping("/{id}/enabled")
    public StudentView enable(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.Enabled input) {
        return service.enable(actor, id, input);
    }
}
