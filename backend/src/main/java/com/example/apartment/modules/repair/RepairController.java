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
    @GetMapping("/maintainers")
    public com.example.apartment.modules.identity.AccountAccess.MaintainerPage maintainers(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.maintainers(actor, q, page, size);
    }
    public record Assign(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long assigneeId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long version) {}
    @PostMapping("/{id}/assign")
    public Detail assign(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @jakarta.validation.Valid @RequestBody Assign input) {
        return service.assign(actor, id, input.assigneeId(), input.version());
    }
    public record Reassign(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long assigneeId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long version,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {}
    public record Start(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long version) {}
    public record Complete(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long version,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String completionNote) {}
    @PostMapping("/{id}/reassign")
    public Detail reassign(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @jakarta.validation.Valid @RequestBody Reassign input) {
        return service.reassign(actor, id, input.assigneeId(), input.version(), input.reason());
    }
    @PostMapping("/{id}/start")
    public Detail start(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @jakarta.validation.Valid @RequestBody Start input) {
        return service.start(actor, id, input.version());
    }
    @PostMapping("/{id}/complete")
    public Detail complete(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @jakarta.validation.Valid @RequestBody Complete input) {
        return service.complete(actor, id, input.version(), input.completionNote());
    }
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
