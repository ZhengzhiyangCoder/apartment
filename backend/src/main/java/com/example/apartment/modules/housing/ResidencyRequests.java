package com.example.apartment.modules.housing;

import jakarta.validation.constraints.*;

public final class ResidencyRequests {
    private ResidencyRequests() {}
    public record CheckIn(@NotNull @Positive Long studentId, @NotNull @Positive Long bedId) {}
    public record Transfer(@NotNull @Positive Long bedId, @NotBlank @Size(max = 200) String note) {}
    public record CheckOut(@NotBlank @Size(max = 200) String note) {}
}

