package com.example.apartment.modules.identity;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AccountPrincipal implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;
    private final long id;
    private final String username;
    private final Role role;
    private final boolean enabled;
    private final long authVersion;
    private String passwordHash;

    public AccountPrincipal(UserAccount account) {
        id = account.id(); username = account.username(); role = account.role();
        enabled = account.enabled(); authVersion = account.authVersion(); passwordHash = account.passwordHash();
    }
    public long id() { return id; }
    public Role role() { return role; }
    public long authVersion() { return authVersion; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public void eraseCredentials() { passwordHash = null; }
}
