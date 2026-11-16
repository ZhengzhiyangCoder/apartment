package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SchemeViews {
    private SchemeViews() {}
    public enum Status { DRAFT, ACTIVE, ARCHIVED }
    public record Scheme(long id, String name, BigDecimal threshold, Status status, long version, Long sourceId,
            Instant createdAt, Instant updatedAt, Instant activatedAt, BigDecimal totalWeight, int dimensionCount) {}
    public record Dimension(long id, String name, BigDecimal weight, int sortOrder) {}
    public record Detail(@com.fasterxml.jackson.annotation.JsonUnwrapped Scheme scheme, List<Dimension> dimensions) {}
    public record Page(List<Scheme> items, long total, int page, int size, Scheme active) {}
}
