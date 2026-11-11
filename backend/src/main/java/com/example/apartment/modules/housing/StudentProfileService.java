package com.example.apartment.modules.housing;

import java.util.List;
import com.example.apartment.modules.identity.AccountAccess;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.support.audit.AuditWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.housing.HousingCatalogService.*;

@Service
public class StudentProfileService {
    private final StudentProfileMapper profiles;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    public StudentProfileService(StudentProfileMapper profiles, AccountAccess accounts, AuditWriter audit) {
        this.profiles = profiles; this.accounts = accounts; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public HousingPage<StudentView> list(String q, Boolean enabled, int page, int size) {
        q = HousingPage.validate(q, page, size);
        var rows = profiles.list(q, enabled, size, (long) (page - 1) * size);
        var summaries = accounts.summaries(rows.stream().map(StudentProfile::userId).toList());
        return new HousingPage<>(rows.stream().map(p -> StudentView.of(p, summaries.get(p.userId()))).toList(), profiles.count(q, enabled), page, size);
    }
    @Transactional
    public StudentView create(AccountPrincipal actor, StudentRequests.Create input) {
        accounts.lockAdministrator(actor); accounts.lockEnabledStudent(input.userId());
        try { profiles.create(input.userId(), input.studentNo().strip(), input.fullName().strip(), input.college().strip(), input.major().strip(), input.className().strip()); }
        catch (DuplicateKeyException e) { throw duplicate("学号或关联账号已存在学生资料，请检查后重试"); }
        var after = profiles.lock(profiles.insertedId());
        audit.record(actor.id(), "STUDENT_CREATED", "STUDENT", after.id(), null, after);
        return view(after);
    }
    @Transactional
    public StudentView update(AccountPrincipal actor, long id, StudentRequests.Update input) {
        accounts.lockAdministrator(actor);
        var before = required(profiles.lock(id)); checkVersion(before.version(), input.version());
        try { profiles.update(id, input.studentNo().strip(), input.fullName().strip(), input.college().strip(), input.major().strip(), input.className().strip()); }
        catch (DuplicateKeyException e) { throw duplicate("学号已存在，请检查后重试"); }
        var after = profiles.lock(id);
        audit.record(actor.id(), "STUDENT_UPDATED", "STUDENT", id, before, after);
        return view(after);
    }
    @Transactional
    public StudentView enable(AccountPrincipal actor, long id, HousingRequests.Enabled input) {
        accounts.lockAdministrator(actor);
        var before = required(profiles.lock(id)); checkVersion(before.version(), input.version());
        if (before.enabled() == input.enabled()) return view(before);
        if (input.enabled()) accounts.lockEnabledStudent(before.userId());
        // Task 04 adds the current residence guard before deactivation.
        profiles.enable(id, input.enabled());
        var after = profiles.lock(id);
        audit.record(actor.id(), input.enabled() ? "STUDENT_ENABLED" : "STUDENT_DISABLED", "STUDENT", id, before, after);
        return view(after);
    }
    private StudentView view(StudentProfile profile) {
        return StudentView.of(profile, accounts.summaries(List.of(profile.userId())).get(profile.userId()));
    }
}
