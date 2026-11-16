package com.example.apartment.modules.housing;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.AccountAccess;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.support.audit.AuditWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.housing.HousingCatalogService.*;

@Service
public class ResidencyService {
    private final ResidencyMapper residencies;
    private final StudentProfileMapper profiles;
    private final HousingCatalogMapper catalog;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    public ResidencyService(ResidencyMapper residencies, StudentProfileMapper profiles, HousingCatalogMapper catalog,
            AccountAccess accounts, AuditWriter audit) {
        this.residencies = residencies; this.profiles = profiles; this.catalog = catalog; this.accounts = accounts; this.audit = audit;
    }
    @Transactional(readOnly=true) public ResidencyView detail(long id) { return required(residencies.find(id)); }
    @Transactional(readOnly = true)
    public HousingPage<ResidencyView> list(String q, Long studentId, Boolean current, int page, int size) {
        q = HousingPage.validate(q, page, size); validId(studentId);
        return new HousingPage<>(residencies.list(q, studentId, current, size, (long) (page - 1) * size),
                residencies.count(q, studentId, current), page, size);
    }
    @Transactional(readOnly = true)
    public HousingPage<HousingViews.Bed> vacantBeds(String q, int page, int size) {
        q = HousingPage.validate(q, page, size);
        return new HousingPage<>(residencies.vacantBeds(q, size, (long) (page - 1) * size), residencies.countVacantBeds(q), page, size);
    }
    @Transactional
    public ResidencyView checkIn(AccountPrincipal actor, ResidencyRequests.CheckIn input) {
        accounts.lockAdministrator(actor);
        var after = createResidence(actor, input.studentId(), input.bedId());
        audit.record(actor.id(), "RESIDENCY_CHECKED_IN", "RESIDENCY", after.id(), null, after);
        return after;
    }
    @Transactional
    public ResidencyView transfer(AccountPrincipal actor, long id, ResidencyRequests.Transfer input) {
        accounts.lockAdministrator(actor);
        var before = requireCurrent(id);
        if (before.bedId() == input.bedId()) throw conflict("目标床位与当前床位相同，请选择其他空床位");
        end(actor, before, "TRANSFER", input.note());
        var after = createResidence(actor, before.studentId(), input.bedId());
        audit.record(actor.id(), "RESIDENCY_TRANSFERRED", "RESIDENCY", id, before,
                java.util.Map.of("previous", residencies.find(id), "current", after));
        return after;
    }
    @Transactional
    public ResidencyView checkOut(AccountPrincipal actor, long id, ResidencyRequests.CheckOut input) {
        accounts.lockAdministrator(actor);
        var before = requireCurrent(id);
        end(actor, before, "CHECKOUT", input.note());
        var after = residencies.find(id);
        audit.record(actor.id(), "RESIDENCY_CHECKED_OUT", "RESIDENCY", id, before, after);
        return after;
    }
    private ResidencyView requireCurrent(long id) {
        var current = required(residencies.find(id));
        if (current.endedAt() != null) throw conflict("该住宿关系已结束，请刷新列表后重试");
        return current;
    }
    private void end(AccountPrincipal actor, ResidencyView before, String reason, String note) {
        if (residencies.end(before.id(), actor.id(), reason, note.strip()) != 1) throw conflict("住宿关系已变化，请刷新后重试");
    }
    private ResidencyView createResidence(AccountPrincipal actor, long studentId, long bedId) {
        var student = required(profiles.lock(studentId));
        if (!student.enabled()) throw conflict("学生资料已停用，请先启用");
        accounts.lockEnabledStudent(student.userId());
        var bed = required(catalog.bed(bedId));
        if (!bed.enabled() || !bed.roomEnabled() || !bed.buildingEnabled()) throw conflict("楼栋、寝室和床位均须启用后才能入住");
        if (residencies.occupied(student.id(), bed.id()) > 0) throw conflict("学生已有当前住宿或床位已被占用，请刷新后重试");
        try { residencies.create(student.id(), bed.id(), bed.buildingName(), bed.roomNo(), bed.bedNo(), actor.id()); }
        catch (DuplicateKeyException e) { throw conflict("学生已有当前住宿或床位已被占用，请刷新后重试"); }
        return residencies.find(residencies.insertedId());
    }
    static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "RESIDENCY_CONFLICT", message); }
}
