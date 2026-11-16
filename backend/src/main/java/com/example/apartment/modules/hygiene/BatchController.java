package com.example.apartment.modules.hygiene;

import com.example.apartment.modules.identity.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.example.apartment.modules.hygiene.BatchViews.*;

@RestController
@RequestMapping("/api/hygiene/batches")
public class BatchController {
    private final BatchService service;
    public BatchController(BatchService service) { this.service=service; }
    @GetMapping public Page list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) { return service.list(page,size); }
    @GetMapping("/current") public Current current() { return service.current(); }
    @PostMapping("/current/prepare") public Detail prepare(@AuthenticationPrincipal AccountPrincipal actor) { return service.prepare(actor); }
    @GetMapping("/{id}") public Detail detail(@PathVariable long id) { return service.detail(id); }
    @PostMapping("/{id}/state") public Detail state(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id,
            @jakarta.validation.Valid @RequestBody BatchRequests.State input) { return service.state(actor,id,input); }
    @PostMapping("/{id}/rooms/{roomId}/add") public Detail add(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@PathVariable long roomId,
            @jakarta.validation.Valid @RequestBody BatchRequests.Roster input) { return service.adjustRoom(actor,id,roomId,input,true); }
    @PostMapping("/{id}/rooms/{roomId}/remove") public Detail remove(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@PathVariable long roomId,
            @jakarta.validation.Valid @RequestBody BatchRequests.Roster input) { return service.adjustRoom(actor,id,roomId,input,false); }
    @GetMapping("/{id}/eligible-rooms") public EligiblePage eligible(@PathVariable long id,@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) { return service.eligibleRooms(id,q,page,size); }
    @GetMapping("/{id}/changes") public com.example.apartment.support.audit.BatchChangeQuery.Page changes(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) { return service.changes(id,page,size); }
    @GetMapping("/{id}/rooms") public RoomPage rooms(@PathVariable long id, @RequestParam(required=false) Long buildingId,
            @RequestParam(defaultValue="") String q, @RequestParam(required=false) Boolean inspected, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        return service.rooms(id,buildingId,q,inspected,page,size);
    }
    @GetMapping("/{id}/rooms/{roomId}") public RoomDetail room(@PathVariable long id, @PathVariable long roomId) { return service.room(id,roomId); }
    @PostMapping("/{id}/rooms/{roomId}/score")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Score submit(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @PathVariable long roomId,
            @jakarta.validation.Valid @RequestBody ScoreRequests.Submit input) { return service.submit(actor,id,roomId,input); }

}
