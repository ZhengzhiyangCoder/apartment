package com.example.apartment.modules.housing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.apartment.modules.identity.AccountPrincipal;
import static com.example.apartment.modules.housing.HousingViews.*;

@RestController
@RequestMapping("/api/housing")
public class HousingCatalogController {
    private final HousingCatalogService service;
    public HousingCatalogController(HousingCatalogService service) { this.service = service; }
    @GetMapping("/buildings/{id}") public Building building(@PathVariable long id) { return service.building(id); }
    @GetMapping("/rooms/{id}") public Room room(@PathVariable long id) { return service.room(id); }
    @GetMapping("/beds/{id}") public Bed bed(@PathVariable long id) { return service.bed(id); }
    @GetMapping("/buildings")
    public HousingPage<Building> buildings(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.buildings(q, enabled, page, size);
    }
    @PostMapping("/buildings") @ResponseStatus(HttpStatus.CREATED)
    public Building createBuilding(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody HousingRequests.BuildingCreate input) {
        return service.createBuilding(actor, input);
    }
    @PostMapping("/buildings/{id}")
    public Building updateBuilding(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.BuildingUpdate input) {
        return service.updateBuilding(actor, id, input);
    }
    @PostMapping("/buildings/{id}/enabled")
    public Building enableBuilding(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.Enabled input) {
        return service.enableBuilding(actor, id, input);
    }
    @GetMapping("/rooms")
    public HousingPage<Room> rooms(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Boolean enabled, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.rooms(q, buildingId, enabled, page, size);
    }
    @PostMapping("/rooms") @ResponseStatus(HttpStatus.CREATED)
    public Room createRoom(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody HousingRequests.RoomCreate input) {
        return service.createRoom(actor, input);
    }
    @PostMapping("/rooms/{id}")
    public Room updateRoom(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.RoomUpdate input) {
        return service.updateRoom(actor, id, input);
    }
    @PostMapping("/rooms/{id}/enabled")
    public Room enableRoom(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.Enabled input) {
        return service.enableRoom(actor, id, input);
    }
    @GetMapping("/beds")
    public HousingPage<Bed> beds(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long buildingId, @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Boolean enabled, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return service.beds(q, buildingId, roomId, enabled, page, size);
    }
    @PostMapping("/beds") @ResponseStatus(HttpStatus.CREATED)
    public Bed createBed(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody HousingRequests.BedCreate input) {
        return service.createBed(actor, input);
    }
    @PostMapping("/beds/{id}")
    public Bed updateBed(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.BedUpdate input) {
        return service.updateBed(actor, id, input);
    }
    @PostMapping("/beds/{id}/enabled")
    public Bed enableBed(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable long id, @Valid @RequestBody HousingRequests.Enabled input) {
        return service.enableBed(actor, id, input);
    }
}
