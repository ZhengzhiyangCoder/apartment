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
        // Use the same lock order as account mutations, then recheck live authorization.
        management.lockEnabledAdministrators();
        var actor = accounts.lockById(principal.id());
        if (actor == null || !actor.enabled() || actor.role() != Role.SYSTEM_ADMIN || actor.authVersion() != principal.authVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号权限已变化，请重新登录");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEnabledStudent(long id) {
        var account = accounts.lockById(id);
        if (account == null) throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "关联账号不存在");
        if (account.role() != Role.STUDENT || !account.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_ACCOUNT_REQUIRED", "请选择已启用的学生账号");
        }
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, AccountSummary> summaries(java.util.List<Long> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        return accounts.summaries(ids).stream().collect(java.util.stream.Collectors.toMap(AccountSummary::id, a -> a));
    }
}
