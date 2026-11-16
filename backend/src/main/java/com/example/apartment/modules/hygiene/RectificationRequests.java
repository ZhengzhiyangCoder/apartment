package com.example.apartment.modules.hygiene;

import jakarta.validation.constraints.*;

public final class RectificationRequests {
    private RectificationRequests() {}
    public record Note(@NotNull @Min(0) Long version,@NotBlank @Size(max=1000) String note) {}
}
