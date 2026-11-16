package com.example.apartment.modules.hygiene;

import com.example.apartment.modules.identity.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hygiene")
public class DraftController {
    private final BatchService service;
    public DraftController(BatchService service) { this.service=service; }
    @GetMapping("/drafts")
    public DraftDtos.Page list(@AuthenticationPrincipal AccountPrincipal actor, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int size) { return service.drafts(actor,page,size); }
    @GetMapping("/batches/{id}/rooms/{roomId}/draft")
    public DraftDtos.Current current(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @PathVariable long roomId) {
        return new DraftDtos.Current(service.draft(actor,id,roomId));
    }
    @PostMapping("/batches/{id}/rooms/{roomId}/draft")
    public DraftDtos.Draft save(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @PathVariable long roomId,
            @jakarta.validation.Valid @RequestBody DraftDtos.Save input) { return service.saveDraft(actor,id,roomId,input); }
}
