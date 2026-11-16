package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class SchemeRequests {
    private SchemeRequests() {}
    public record Dimension(@NotBlank @Size(max = 64) String name,
            @NotNull BigDecimal weight) {}
    public record Definition(@NotBlank @Size(max = 64) String name,
            BigDecimal threshold,
            @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Dimension> dimensions) {}
    public record Update(@NotNull @Valid Definition definition, @NotNull @PositiveOrZero Long version) {}
    public record Version(@NotNull @PositiveOrZero Long version) {}
    public record Activate(@NotNull @PositiveOrZero Long version, @NotNull @PositiveOrZero Long activeId) {}
}
