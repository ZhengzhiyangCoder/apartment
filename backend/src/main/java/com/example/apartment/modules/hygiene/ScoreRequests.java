package com.example.apartment.modules.hygiene;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ScoreRequests {
    private ScoreRequests() {}
    public record Dimension(@NotNull @Positive Long dimensionId, @NotNull @Min(0) @Max(100) Integer score) {}
    public record Submit(@NotNull @Size(min=1,max=20) List<@NotNull @Valid Dimension> dimensions) {}
}
