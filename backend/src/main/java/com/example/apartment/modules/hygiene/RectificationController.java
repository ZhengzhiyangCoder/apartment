package com.example.apartment.modules.hygiene;

import org.springframework.web.bind.annotation.*;
import com.example.apartment.modules.identity.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import static com.example.apartment.modules.hygiene.RectificationViews.*;

@RestController
@RequestMapping("/api/hygiene/rectifications")
public class RectificationController {
    private final RectificationService service;
    public RectificationController(RectificationService service) { this.service=service; }
    @GetMapping public Page list(@RequestParam(required=false) Status status,@RequestParam(required=false) Long buildingId,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return service.list(status,buildingId,q,page,size);
    }
    @GetMapping("/{id}") public Detail detail(@PathVariable long id) { return service.detail(id); }
    @PostMapping("/{id}/follow-ups") public Detail followUp(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,
            @jakarta.validation.Valid @RequestBody RectificationRequests.Note input) { return service.followUp(actor,id,input); }
    @PostMapping("/{id}/verify") public Detail verify(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,
            @jakarta.validation.Valid @RequestBody RectificationRequests.Note input) { return service.verify(actor,id,input); }
    @PostMapping("/{id}/close") public Detail close(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,
            @jakarta.validation.Valid @RequestBody RectificationRequests.Note input) { return service.close(actor,id,input); }
}
