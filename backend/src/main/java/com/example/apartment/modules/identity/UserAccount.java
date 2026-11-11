package com.example.apartment.modules.identity;

public record UserAccount(Long id, String username, String passwordHash, String displayName,
                          Role role, boolean enabled, long authVersion) { }
