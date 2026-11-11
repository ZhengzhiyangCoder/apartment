package com.example.apartment.modules.identity;

public record AccountView(long id, String username, String displayName, Role role, boolean enabled, long version) {
    static AccountView of(UserAccount account) {
        return new AccountView(account.id(), account.username(), account.displayName(), account.role(), account.enabled(), account.authVersion());
    }
}
