package com.example.apartment.modules.housing;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.AccountAccess;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.support.audit.AuditWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.housing.HousingViews.*;

@Service
public class HousingCatalogService {
    private final HousingCatalogMapper catalog;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    private final ResidencyReferences residencies;
    public HousingCatalogService(HousingCatalogMapper catalog, AccountAccess accounts, AuditWriter audit, ResidencyReferences residencies) {
        this.catalog = catalog; this.accounts = accounts; this.audit = audit; this.residencies = residencies;
    }
    @Transactional(readOnly = true)
    public HousingPage<Building> buildings(String q, Boolean enabled, int page, int size) {
        q = HousingPage.validate(q, page, size);
        return new HousingPage<>(catalog.buildings(q, enabled, size, (long) (page - 1) * size), catalog.countBuildings(q, enabled), page, size);
    }
    @Transactional
    public Building createBuilding(AccountPrincipal actor, HousingRequests.BuildingCreate input) {
        accounts.lockAdministrator(actor);
        try { catalog.createBuilding(input.name().strip()); }
        catch (DuplicateKeyException e) { throw duplicate("楼栋名称已存在"); }
        var after = catalog.building(catalog.insertedId());
        audit.record(actor.id(), "BUILDING_CREATED", "BUILDING", after.id(), null, after);
        return after;
    }
    @Transactional
    public Building updateBuilding(AccountPrincipal actor, long id, HousingRequests.BuildingUpdate input) {
        accounts.lockAdministrator(actor);
        var before = required(catalog.building(id)); checkVersion(before.version(), input.version());
        try { catalog.updateBuilding(id, input.name().strip()); }
        catch (DuplicateKeyException e) { throw duplicate("楼栋名称已存在"); }
        var after = catalog.building(id);
        audit.record(actor.id(), "BUILDING_UPDATED", "BUILDING", id, before, after);
        return after;
    }
    @Transactional
    public Building enableBuilding(AccountPrincipal actor, long id, HousingRequests.Enabled input) {
        accounts.lockAdministrator(actor);
        var before = required(catalog.building(id)); checkVersion(before.version(), input.version());
        if (before.enabled() == input.enabled()) return before;
        if (!input.enabled()) residencies.requireBuildingVacant(id);
        // Task 13 adds unfinished rectification guards.
        catalog.enableBuilding(id, input.enabled());
        var after = catalog.building(id);
        audit.record(actor.id(), input.enabled() ? "BUILDING_ENABLED" : "BUILDING_DISABLED", "BUILDING", id, before, after);
        return after;
    }
    static <T> T required(T value) {
        if (value == null) throw new ApiException(HttpStatus.NOT_FOUND, "HOUSING_NOT_FOUND", "资料不存在，请刷新列表");
        return value;
    }
    static void checkVersion(long actual, long expected) {
        if (actual != expected) throw new ApiException(HttpStatus.CONFLICT, "HOUSING_CHANGED", "资料已被更新，请刷新列表后重试");
    }
    static ApiException duplicate(String message) { return new ApiException(HttpStatus.CONFLICT, "HOUSING_DUPLICATE", message); }

    @Transactional(readOnly = true)
    public HousingPage<Room> rooms(String q, Long buildingId, Boolean enabled, int page, int size) {
        q = HousingPage.validate(q, page, size); validId(buildingId);
        return new HousingPage<>(catalog.rooms(q, buildingId, enabled, size, (long) (page - 1) * size), catalog.countRooms(q, buildingId, enabled), page, size);
    }
    @Transactional
    public Room createRoom(AccountPrincipal actor, HousingRequests.RoomCreate input) {
        accounts.lockAdministrator(actor);
        requireEnabled(required(catalog.building(input.buildingId())).enabled());
        try { catalog.createRoom(input.buildingId(), input.roomNo().strip(), input.floor()); }
        catch (DuplicateKeyException e) { throw duplicate("该楼栋内的房号已存在"); }
        var after = catalog.room(catalog.insertedId());
        audit.record(actor.id(), "ROOM_CREATED", "ROOM", after.id(), null, after);
        return after;
    }
    @Transactional
    public Room updateRoom(AccountPrincipal actor, long id, HousingRequests.RoomUpdate input) {
        accounts.lockAdministrator(actor);
        var before = required(catalog.room(id)); checkVersion(before.version(), input.version());
        try { catalog.updateRoom(id, input.roomNo().strip(), input.floor()); }
        catch (DuplicateKeyException e) { throw duplicate("该楼栋内的房号已存在"); }
        var after = catalog.room(id);
        audit.record(actor.id(), "ROOM_UPDATED", "ROOM", id, before, after);
        return after;
    }
    @Transactional
    public Room enableRoom(AccountPrincipal actor, long id, HousingRequests.Enabled input) {
        accounts.lockAdministrator(actor);
        var before = required(catalog.room(id)); checkVersion(before.version(), input.version());
        if (before.enabled() == input.enabled()) return before;
        if (input.enabled()) requireEnabled(before.buildingEnabled());
        if (!input.enabled()) residencies.requireRoomVacant(id);
        // Task 13 adds unfinished rectification guards.
        catalog.enableRoom(id, input.enabled());
        var after = catalog.room(id);
        audit.record(actor.id(), input.enabled() ? "ROOM_ENABLED" : "ROOM_DISABLED", "ROOM", id, before, after);
        return after;
    }
    @Transactional(readOnly = true)
    public HousingPage<Bed> beds(String q, Long buildingId, Long roomId, Boolean enabled, int page, int size) {
        q = HousingPage.validate(q, page, size); validId(buildingId); validId(roomId);
        return new HousingPage<>(catalog.beds(q, buildingId, roomId, enabled, size, (long) (page - 1) * size), catalog.countBeds(q, buildingId, roomId, enabled), page, size);
    }
    @Transactional
    public Bed createBed(AccountPrincipal actor, HousingRequests.BedCreate input) {
        accounts.lockAdministrator(actor);
        var room = required(catalog.room(input.roomId())); requireEnabled(room.enabled() && room.buildingEnabled());
        try { catalog.createBed(input.roomId(), input.bedNo().strip()); }
        catch (DuplicateKeyException e) { throw duplicate("该寝室内的床位编号已存在"); }
        var after = catalog.bed(catalog.insertedId());
        audit.record(actor.id(), "BED_CREATED", "BED", after.id(), null, after);
        return after;
    }
    @Transactional
    public Bed updateBed(AccountPrincipal actor, long id, HousingRequests.BedUpdate input) {
        accounts.lockAdministrator(actor);
        var before = required(catalog.bed(id)); checkVersion(before.version(), input.version());
        try { catalog.updateBed(id, input.bedNo().strip()); }
        catch (DuplicateKeyException e) { throw duplicate("该寝室内的床位编号已存在"); }
        var after = catalog.bed(id);
        audit.record(actor.id(), "BED_UPDATED", "BED", id, before, after);
        return after;
    }
    @Transactional
    public Bed enableBed(AccountPrincipal actor, long id, HousingRequests.Enabled input) {
        accounts.lockAdministrator(actor);
        var before = required(catalog.bed(id)); checkVersion(before.version(), input.version());
        if (before.enabled() == input.enabled()) return before;
        if (input.enabled()) requireEnabled(before.buildingEnabled() && before.roomEnabled());
        if (!input.enabled()) residencies.requireBedVacant(id);
        catalog.enableBed(id, input.enabled());
        var after = catalog.bed(id);
        audit.record(actor.id(), input.enabled() ? "BED_ENABLED" : "BED_DISABLED", "BED", id, before, after);
        return after;
    }
    static void requireEnabled(boolean enabled) {
        if (!enabled) throw new ApiException(HttpStatus.CONFLICT, "PARENT_DISABLED", "上级资料已停用，请先启用所属楼栋和寝室");
    }
    static void validId(Long id) {
        if (id != null && id < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "资料标识必须是正整数");
    }
}
