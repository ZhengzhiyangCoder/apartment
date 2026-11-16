package com.example.apartment.modules.housing;

import com.example.apartment.modules.identity.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/housing")
public class ResidencyController {
    private final ResidencyService service;
    private final CurrentResidencyQuery current;
    public ResidencyController(ResidencyService service, CurrentResidencyQuery current) { this.service = service; this.current = current; }
    @GetMapping("/residencies/{id}") public ResidencyView detail(@PathVariable long id) { return service.detail(id); }
    @GetMapping("/residencies")
    public HousingPage<ResidencyView> list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Boolean current, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.list(q, studentId, current, page, size);
    }
    @GetMapping("/vacant-beds")
    public HousingPage<HousingViews.Bed> vacantBeds(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.vacantBeds(q, page, size);
    }
    @PostMapping("/residencies") @ResponseStatus(HttpStatus.CREATED)
    public ResidencyView checkIn(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody ResidencyRequests.CheckIn input) {
        return service.checkIn(actor, input);
    }
    @PostMapping("/residencies/{id}/transfer")
    public ResidencyView transfer(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody ResidencyRequests.Transfer input) {
        return service.transfer(actor, id, input);
    }
    @PostMapping("/residencies/{id}/checkout")
    public ResidencyView checkOut(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody ResidencyRequests.CheckOut input) {
        return service.checkOut(actor, id, input);
    }
    public record MyResidency(ResidencyView current) {}
    @GetMapping("/my-residency")
    public MyResidency mine(@AuthenticationPrincipal AccountPrincipal actor) { return new MyResidency(current.findForStudent(actor)); }
}
