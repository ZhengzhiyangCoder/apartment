package com.example.apartment.modules.housing;

import jakarta.validation.constraints.*;

public final class StudentRequests {
    private StudentRequests() {}
    public record Create(@NotNull @Positive Long userId,
            @NotBlank @Size(max = 32) String studentNo, @NotBlank @Size(max = 64) String fullName,
            @NotBlank @Size(max = 64) String college, @NotBlank @Size(max = 64) String major,
            @NotBlank @Size(max = 64) String className) {}
    public record Update(@NotBlank @Size(max = 32) String studentNo, @NotBlank @Size(max = 64) String fullName,
            @NotBlank @Size(max = 64) String college, @NotBlank @Size(max = 64) String major,
            @NotBlank @Size(max = 64) String className, @NotNull @PositiveOrZero Long version) {}
}
