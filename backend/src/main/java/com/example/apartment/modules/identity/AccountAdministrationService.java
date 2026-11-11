package com.example.apartment.modules.identity;

import com.example.apartment.common.ApiException;
import com.example.apartment.support.audit.AuditWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdministrationService {
    private final AccountAdministrationMapper management;
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final AuditWriter audit;
    public AccountAdministrationService(AccountAdministrationMapper management, AccountMapper accounts,
            PasswordEncoder passwords, AuditWriter audit) {
        this.management = management; this.accounts = accounts; this.passwords = passwords; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AccountPage list(String q, Role role, Boolean enabled, int page, int size) {
        if (q.length() > 64 || page < 1 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "关键词最多 64 字，页码至少为 1，每页 1–100 条");
        }
        return new AccountPage(management.list(q.strip(), role, enabled, size, (long) (page - 1) * size),
                management.count(q.strip(), role, enabled), page, size);
    }

    @Transactional
    public AccountView create(AccountPrincipal actor, AccountRequests.Create input) {
        validatePassword(input.password());
        String hash = passwords.encode(input.password());
        management.lockEnabledAdministrators();
        requireAdministrator(actor);
        try {
            management.create(input.username(), input.displayName().strip(), input.role(), hash);
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "账号已存在，请使用其他账号名");
        }
        var result = AccountView.of(accounts.findByUsername(input.username()));
        audit.record(actor.id(), "ACCOUNT_CREATED", "ACCOUNT", result.id(), null, result);
        return result;
    }

    @Transactional
    public AccountView changeRole(AccountPrincipal actor, long id, AccountRequests.ChangeRole input) {
        var before = lockTarget(actor, id, input.version());
        if (before.role() == input.role()) return AccountView.of(before);
        if (input.role() != Role.SYSTEM_ADMIN) protectLastAdministrator(before);
        management.changeRole(id, input.role());
        return recordChange(actor, "ACCOUNT_ROLE_CHANGED", before);
    }

    @Transactional
    public AccountView changeEnabled(AccountPrincipal actor, long id, AccountRequests.ChangeEnabled input) {
        var before = lockTarget(actor, id, input.version());
        if (before.enabled() == input.enabled()) return AccountView.of(before);
        if (!input.enabled()) protectLastAdministrator(before);
        // Housing / repair / hygiene add their active-business checks here when those modules exist.
        // Audit references are historical: retain the identity, never delete it.
        management.changeEnabled(id, input.enabled());
        return recordChange(actor, input.enabled() ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", before);
    }

    @Transactional
    public AccountView resetPassword(AccountPrincipal actor, long id, AccountRequests.ResetPassword input) {
        validatePassword(input.newPassword());
        String hash = passwords.encode(input.newPassword());
        var before = lockTarget(actor, id, input.version());
        management.resetPassword(id, hash);
        return recordChange(actor, "ACCOUNT_PASSWORD_RESET", before);
    }

    private UserAccount lockTarget(AccountPrincipal actor, long id, long version) {
        // Serialize administrator mutations before locking the target; two concurrent demotions
        // must not both observe another available administrator.
        management.lockEnabledAdministrators();
        requireAdministrator(actor);
        var target = accounts.lockById(id);
        if (target == null) throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在，请刷新列表");
        if (target.authVersion() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "账号已被更新，请刷新列表后重试");
        }
        return target;
    }

    private void protectLastAdministrator(UserAccount target) {
        if (target.enabled() && target.role() == Role.SYSTEM_ADMIN && management.count("", Role.SYSTEM_ADMIN, true) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMINISTRATOR", "请先创建或启用另一位系统管理员，再停用或变更此账号角色");
        }
    }

    private AccountView recordChange(AccountPrincipal actor, String action, UserAccount before) {
        var after = AccountView.of(accounts.findById(before.id()));
        audit.record(actor.id(), action, "ACCOUNT", before.id(), AccountView.of(before), after);
        return after;
    }

    private void requireAdministrator(AccountPrincipal principal) {
        var actor = accounts.findById(principal.id());
        if (actor == null || !actor.enabled() || actor.role() != Role.SYSTEM_ADMIN || actor.authVersion() != principal.authVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号权限已变化，请重新登录");
        }
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "密码的 UTF-8 长度不能超过 72 字节");
        }
    }
}
