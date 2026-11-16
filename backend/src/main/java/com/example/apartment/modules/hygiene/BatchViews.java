package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.*;
import java.util.List;

public final class BatchViews {
    private BatchViews() {}
    public enum Status { OPEN, CLOSED, SUSPENDED }
    public record Week(int isoYear, int isoWeek, LocalDate weekStart) {
        static Week at(Instant now) {
            var date = now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            return new Week(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        }
    }
    public record Batch(long id, int isoYear, int isoWeek, LocalDate weekStart, long schemeId, String schemeName,
            BigDecimal threshold, Status status, long createdBy, Instant createdAt,
            long version, boolean historicalReopened, Status suspendedFrom, long expectedCount, long inspectedCount) {}
    public record Detail(@com.fasterxml.jackson.annotation.JsonUnwrapped Batch batch, boolean scoringAllowed,
            BigDecimal completionRate, List<SchemeViews.Dimension> dimensions, List<Building> buildings) {}
    public record Current(@com.fasterxml.jackson.annotation.JsonUnwrapped Week week, Detail batch) {}
    public record Building(long id, String name) {}
    public record Room(long roomId, long buildingId, String buildingName, String roomNo, Long scoreId, BigDecimal totalScore) {}
    public record ScoreRow(long id, long batchId, long roomId, BigDecimal totalScore, BigDecimal threshold, long submittedBy, Instant submittedAt, long version) {}
    public record ScoredDimension(long dimensionId, String name, BigDecimal weight, int score, int sortOrder) {}
    public record Score(@com.fasterxml.jackson.annotation.JsonUnwrapped ScoreRow summary, List<ScoredDimension> dimensions) {}
    public record RoomDetail(Room room, Score score) {}
    public record RoomPage(List<Room> items, long total, int page, int size) {}
    public record Page(List<Detail> items, long total, int page, int size) {}
    public record EligiblePage(List<com.example.apartment.modules.housing.InspectionHousingQuery.OccupiedRoom> items, long total, int page, int size) {}
}
