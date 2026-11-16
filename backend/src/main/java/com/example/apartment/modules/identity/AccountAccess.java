package com.example.apartment.modules.identity;

import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Controlled identity boundary for transactional business writes. Never exposes credentials. */
@Service
public class AccountAccess {
    private final AccountMapper accounts;
    private final AccountAdministrationMapper management;
    public AccountAccess(AccountMapper accounts, AccountAdministrationMapper management) {
        this.accounts = accounts; this.management = management;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAdministrator(AccountPrincipal principal) {
        lockBusinessActor(principal, Role.SYSTEM_ADMIN);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountView lockBusinessActor(AccountPrincipal principal, Role... allowed) {
        // Use the same lock order as account mutations, then recheck live authorization.
        management.lockEnabledAdministrators();
        var actor = accounts.lockById(principal.id());
        if (actor == null || !actor.enabled() || actor.role() != principal.role() || actor.authVersion() != principal.authVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号权限已变化，请重新登录");
        }
        if (java.util.Arrays.stream(allowed).noneMatch(role -> role == actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有操作该业务的权限");
        }
        return AccountView.of(actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEnabledStudent(long id) {
        var account = accounts.lockById(id);
        if (account == null) throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "关联账号不存在");
        if (account.role() != Role.STUDENT || !account.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_ACCOUNT_REQUIRED", "请选择已启用的学生账号");
        }
    }

    public record Maintainer(long id, String username, String displayName) {}
    public record MaintainerPage(java.util.List<Maintainer> items, long total, int page, int size) {}

    @Transactional(readOnly = true)
    public MaintainerPage enabledMaintainers(String q, int page, int size) {
        if (q.length() > 64 || page < 1 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "关键词最多 64 字，页码至少为 1，每页 1–100 条");
        }
        return new MaintainerPage(accounts.enabledMaintainers(q.strip(), size, (long) (page - 1) * size),
                management.count(q.strip(), Role.MAINTAINER, true), page, size);
    }

    /** Caller holds the shared business lock, so role/enable changes cannot race assignment. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Maintainer lockEnabledMaintainer(long id) {
        var account = accounts.lockById(id);
        if (account == null || !account.enabled() || account.role() != Role.MAINTAINER) {
            throw new ApiException(HttpStatus.CONFLICT, "MAINTAINER_REQUIRED", "请选择已启用的维修人员，刷新人员列表后重试");
        }
        return new Maintainer(account.id(), account.username(), account.displayName());
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, AccountSummary> summaries(java.util.List<Long> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        return accounts.summaries(ids).stream().collect(java.util.stream.Collectors.toMap(AccountSummary::id, a -> a));
    }
}
