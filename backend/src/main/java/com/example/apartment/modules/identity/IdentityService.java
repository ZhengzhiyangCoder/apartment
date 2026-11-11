package com.example.apartment.modules.identity;

import com.example.apartment.common.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    public IdentityService(AccountMapper accounts, PasswordEncoder passwords) {
        this.accounts = accounts; this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public ProfileView profile(AccountPrincipal principal) {
        var account = requireCurrentAccount(principal);
        return new ProfileView(account.id(), account.username(), account.displayName(), account.role(), account.role().label());
    }

    @Transactional(readOnly = true)
    public boolean isSessionValid(AccountPrincipal principal) {
        return matchesSession(accounts.findById(principal.id()), principal);
    }

    @Transactional
    public void changePassword(AccountPrincipal principal, ChangePasswordRequest input) {
        var account = requireCurrentAccount(principal);
        if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "密码的 UTF-8 长度不能超过 72 字节");
        }
        if (!passwords.matches(input.currentPassword(), account.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "当前密码不正确");
        }
        if (passwords.matches(input.newPassword(), account.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        if (accounts.changePassword(account.id(), account.authVersion(), passwords.encode(input.newPassword())) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "账号已发生变化，请重新登录后再试");
        }
    }

    private UserAccount requireCurrentAccount(AccountPrincipal principal) {
        var account = accounts.findById(principal.id());
        if (!matchesSession(account, principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录状态已失效，请重新登录");
        }
        return account;
    }

    private boolean matchesSession(UserAccount account, AccountPrincipal principal) {
        return account != null && account.enabled() && account.role() == principal.role()
                && account.authVersion() == principal.authVersion();
    }
}
