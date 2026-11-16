package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.*;
import com.example.apartment.support.audit.AuditWriter;
import java.math.BigDecimal;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.hygiene.SchemeViews.*;

@Service
public class SchemeService {
    private final SchemeMapper schemes;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    public SchemeService(SchemeMapper schemes, AccountAccess accounts, AuditWriter audit) {
        this.schemes = schemes; this.accounts = accounts; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public Page list(AccountPrincipal actor, String q, Status status, int page, int size) {
        requireReader(actor);
        if (q.length() > 64 || page < 1 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "关键词最多64字，页码至少为1，每页1–100条");
        boolean publishedOnly = actor.role() == Role.DORM_MANAGER;
        return new Page(schemes.list(publishedOnly, q.strip(), status, size, (long) (page - 1) * size),
                schemes.count(publishedOnly, q.strip(), status), page, size, schemes.active());
    }
    @Transactional(readOnly = true)
    public Detail detail(AccountPrincipal actor, long id) {
        requireReader(actor);
        var scheme = schemes.find(id);
        if (scheme == null || (actor.role() == Role.DORM_MANAGER && scheme.status() == Status.DRAFT)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCHEME_NOT_FOUND", "评分方案不存在或无访问权限");
        }
        return new Detail(scheme, schemes.dimensions(id));
    }
    @Transactional
    public Detail create(AccountPrincipal actor, SchemeRequests.Definition input) {
        accounts.lockAdministrator(actor);
        validateDefinition(input);
        schemes.create(input.name().strip(), threshold(input), null);
        long id = schemes.insertedId();
        saveDimensions(id, input);
        var after = detail(actor, id);
        audit.record(actor.id(), "SCHEME_CREATED", "SCHEME", id, null, after);
        return after;
    }
    @Transactional
    public Detail update(AccountPrincipal actor, long id, SchemeRequests.Update input) {
        accounts.lockAdministrator(actor);
        var before = detail(actor, id);
        requireDraft(before.scheme(), input.version());
        validateDefinition(input.definition());
        if (schemes.update(id, input.version(), input.definition().name().strip(), threshold(input.definition())) != 1) throw changed();
        schemes.clearDraftDimensions(id);
        saveDimensions(id, input.definition());
        var after = detail(actor, id);
        audit.record(actor.id(), "SCHEME_UPDATED", "SCHEME", id, before, after);
        return after;
    }
    @Transactional
    public Detail copy(AccountPrincipal actor, long id, long version) {
        accounts.lockAdministrator(actor);
        var source = detail(actor, id);
        if (source.scheme().version() != version) throw changed();
        schemes.create(source.scheme().name(), source.scheme().threshold(), id);
        long nextId = schemes.insertedId();
        for (var d : source.dimensions()) schemes.insertDimension(nextId, d.name(), d.weight(), d.sortOrder());
        var after = detail(actor, nextId);
        audit.record(actor.id(), "SCHEME_COPIED", "SCHEME", nextId, source, after);
        return after;
    }
    @Transactional
    public Detail activate(AccountPrincipal actor, long id, SchemeRequests.Activate input) {
        accounts.lockAdministrator(actor);
        var before = detail(actor, id);
        requireDraft(before.scheme(), input.version());
        if (before.scheme().totalWeight().compareTo(new BigDecimal("100")) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEIGHT_TOTAL", "启用前维度权重合计必须等于100%，请先编辑草稿");
        }
        var current = schemes.active();
        if ((current == null ? 0 : current.id()) != input.activeId()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_SCHEME_CHANGED", "当前启用方案已变化，请刷新后重新确认切换");
        }
        Detail previous = current == null ? null : detail(actor, current.id());
        if (current != null && schemes.archive(current.id(), current.version()) != 1) throw changed();
        if (schemes.activate(id, input.version()) != 1) throw changed();
        if (previous != null) audit.record(actor.id(), "SCHEME_ARCHIVED", "SCHEME", current.id(), previous, detail(actor, current.id()));
        var after = detail(actor, id);
        audit.record(actor.id(), "SCHEME_ACTIVATED", "SCHEME", id, before, after);
        return after;
    }
    private void saveDimensions(long id, SchemeRequests.Definition input) {
        for (int i = 0; i < input.dimensions().size(); i++) {
            var d = input.dimensions().get(i); schemes.insertDimension(id, d.name().strip(), d.weight(), i + 1);
        }
    }
    private BigDecimal threshold(SchemeRequests.Definition input) { return input.threshold() == null ? new BigDecimal("60.00") : input.threshold(); }
    private void validateDefinition(SchemeRequests.Definition input) {
        var names = new HashSet<String>();
        for (var d : input.dimensions()) {
            validatePercentage(d.weight(), "INVALID_WEIGHT", "维度权重");
            if (!names.add(d.name().strip())) throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_DIMENSION", "同一方案的维度名称不能重复");
        }
        validatePercentage(threshold(input), "INVALID_THRESHOLD", "整改阈值");
    }
    private void validatePercentage(BigDecimal value, String code, String label) {
        if (value.scale() > 2 || value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, label + "必须在0–100之间，最多两位小数");
        }
    }
    private void requireDraft(Scheme scheme, long version) {
        if (scheme.status() != Status.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "SCHEME_FROZEN", "已启用或历史版本不可修改，请复制为新草稿后调整");
        if (scheme.version() != version) throw changed();
    }
    private ApiException changed() { return new ApiException(HttpStatus.CONFLICT, "SCHEME_CHANGED", "方案已变化，请刷新后重试，当前输入可保留"); }
    private void requireReader(AccountPrincipal actor) {
        if (actor.role() != Role.SYSTEM_ADMIN && actor.role() != Role.DORM_MANAGER) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有查看评分标准的权限");
    }
}
