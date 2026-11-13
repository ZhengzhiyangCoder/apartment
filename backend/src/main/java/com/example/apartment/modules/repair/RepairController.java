package com.example.apartment.modules.repair;

import com.example.apartment.modules.identity.AccountPrincipal;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.example.apartment.modules.repair.RepairViews.*;

@RestController
@RequestMapping("/api/repairs")
public class RepairController {
    private final RepairService service;
    public RepairController(RepairService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Detail submit(@AuthenticationPrincipal AccountPrincipal actor, @RequestParam Category category,
            @RequestParam String description, @RequestParam String requestId,
            @RequestParam(required = false) List<MultipartFile> images) {
        return service.submit(actor, category, description, requestId, images == null ? List.of() : images);
    }
    @GetMapping
    public Page list(@AuthenticationPrincipal AccountPrincipal actor, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Status status, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.list(actor, q, status, page, size);
    }
    @GetMapping("/{id}")
    public Detail detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id) { return service.detail(actor, id); }
    public record Cancel(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {}
    @PostMapping("/{id}/cancel")
    public Detail cancel(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @jakarta.validation.Valid @RequestBody Cancel input) {
        return service.cancel(actor, id, input.reason());
    }
    @GetMapping("/{id}/images/{image}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @PathVariable long image) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"repair-" + image + ".png\"").body(service.image(actor, id, image));
    }
}
