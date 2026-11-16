package com.example.apartment.modules.hygiene;

import com.example.apartment.modules.identity.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.example.apartment.modules.hygiene.SchemeViews.*;

@RestController
@RequestMapping("/api/hygiene/schemes")
public class SchemeController {
    private final SchemeService service;
    public SchemeController(SchemeService service) { this.service = service; }
    @GetMapping
    public Page list(@AuthenticationPrincipal AccountPrincipal actor, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Status status, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.list(actor, q, status, page, size);
    }
    @GetMapping("/{id}")
    public Detail detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id) { return service.detail(actor, id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody SchemeRequests.Definition input) { return service.create(actor, input); }
    @PostMapping("/{id}")
    public Detail update(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody SchemeRequests.Update input) { return service.update(actor, id, input); }
    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public Detail copy(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody SchemeRequests.Version input) { return service.copy(actor, id, input.version()); }
    @PostMapping("/{id}/activate")
    public Detail activate(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody SchemeRequests.Activate input) { return service.activate(actor, id, input); }
}
