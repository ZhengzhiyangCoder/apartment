package com.example.apartment.modules.hygiene;

import com.example.apartment.modules.identity.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hygiene/scores/{id}")
public class CorrectionController {
    private final CorrectionService service;
    public CorrectionController(CorrectionService service) { this.service=service; }
    @GetMapping public BatchViews.Score detail(@PathVariable long id) { return service.detail(id); }
    @GetMapping("/corrections") public CorrectionViews.Page history(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) { return service.history(id,page,size); }
    @PostMapping("/corrections") public BatchViews.Score correct(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@Valid @RequestBody ScoreRequests.Correct input) { return service.correct(actor,id,input); }
}
