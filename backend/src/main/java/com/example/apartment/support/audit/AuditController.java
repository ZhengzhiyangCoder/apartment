package com.example.apartment.support.audit;

import com.example.apartment.modules.identity.AccountPrincipal;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.example.apartment.support.audit.AuditQuery.*;

@RestController
@RequestMapping("/api/audit/records")
public class AuditController {
    private final AuditQuery query;
    public AuditController(AuditQuery query) { this.query=query; }
    @GetMapping public Page list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(required=false) Long actorId,@RequestParam(required=false) Target targetType,@RequestParam(required=false) Long targetId,
            @RequestParam(required=false) Action action,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant until,
            @RequestParam(defaultValue="NEWEST") Sort sort,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return query.list(actor,new Filter(actorId,targetType,targetId,action,from,until,sort,page,size));
    }
    @GetMapping("/{id}") public Detail detail(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) { return query.detail(actor,id); }
}
