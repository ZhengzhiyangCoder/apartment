package com.example.apartment.modules.identity;

import jakarta.validation.constraints.*;

public final class AccountRequests {
    private AccountRequests() {}
    public record Create(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
            @NotBlank @Size(max = 64) String displayName,
            @NotNull Role role,
            @NotBlank @Size(min = 8, max = 64) String password) {}
    public record ChangeRole(@NotNull Role role, @NotNull @PositiveOrZero Long version) {}
    public record ChangeEnabled(@NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) {}
    public record ResetPassword(@NotBlank @Size(min = 8, max = 64) String newPassword,
            @NotNull @PositiveOrZero Long version) {}
}
