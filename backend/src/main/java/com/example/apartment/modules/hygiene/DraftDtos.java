package com.example.apartment.modules.hygiene;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class DraftDtos {
    private DraftDtos() {}
    // Raw values deliberately allow incomplete/invalid scores; Submit remains the formal validation boundary.
    public record Value(@NotNull @Positive Long dimensionId, @NotNull @Size(max=4) String value) {}
    public record Save(@NotNull @Min(0) Long version, @NotNull @Size(max=20) List<@NotNull @Valid Value> dimensions) {}
    public record Row(long batchId, long roomId, String buildingName, String roomNo, int isoYear, int isoWeek,
            BatchViews.Status status, long version, Instant updatedAt, String dimensions) {}
    public record Draft(long batchId, long roomId, String buildingName, String roomNo, int isoYear, int isoWeek,
            BatchViews.Status status, long version, Instant updatedAt, List<Value> dimensions) {}
    public record Current(Draft draft) {}
    public record Page(List<Draft> items, long total, int page, int size) {}
}
