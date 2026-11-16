package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RectificationViews {
    private RectificationViews() {}
    public enum Status { OPEN, CLOSED }
    public enum Action { TRIGGERED, FOLLOW_UP, VERIFIED, CLOSED, CORRECTED }
    public record Event(long id, long roomId, long buildingId, String buildingName, String roomNo,
            Status status, long version, Instant createdAt, Instant updatedAt, Instant verifiedAt, Instant closedAt) {}
    public record Trigger(long scoreId, long batchId, int isoYear, int isoWeek, LocalDate weekStart,
            long schemeId, String schemeName, BatchViews.Status batchStatus, BigDecimal totalScore,
            BigDecimal threshold, BigDecimal currentScore, Instant submittedAt, Instant triggeredAt, boolean corrected) {}
    public record Log(long id, Action action, long actorId, String actorName, String note, Instant createdAt) {}
    public record Detail(@com.fasterxml.jackson.annotation.JsonUnwrapped Event event, List<Trigger> triggers, List<Log> logs) {}
    public record Page(List<Event> items, long total, int page, int size) {}
}
