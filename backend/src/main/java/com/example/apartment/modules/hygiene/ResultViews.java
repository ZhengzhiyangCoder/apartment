package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.*;
import java.util.List;

public final class ResultViews {
    private ResultViews() {}
    public enum Kind { WEEK, MONTH }
    public record Period(Kind kind, LocalDate start, LocalDate end, String label) {
        static Period at(Kind kind, LocalDate date) {
            var start=kind==Kind.WEEK ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : date.withDayOfMonth(1);
            var end=kind==Kind.WEEK ? start.plusWeeks(1) : start.plusMonths(1);
            String label=kind==Kind.WEEK ? start.get(IsoFields.WEEK_BASED_YEAR)+"年第"+start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)+"周" : YearMonth.from(start).toString();
            return new Period(kind,start,end,label);
        }
    }
    public record Scope(long buildingId,String buildingName,long roomId,String roomNo) {}
    public record Dimension(String name,BigDecimal weight,Integer score) {}
    public record Week(Period period,Long batchId,String status,String schemeName,BigDecimal threshold,
            BigDecimal totalScore,Instant submittedAt,List<Dimension> dimensions) {}
    public record Sample(BigDecimal score,long samples,Long rank) {}
    public record Ranking(String roomNo,BigDecimal score,long samples,long rank) {}
    public record Rankings(List<Ranking> items,long total,int page,int size) {}
    public record Trend(Period period,BigDecimal score,long samples) {}
    public record Results(Scope scope,Period period,Sample summary,List<Trend> trend,List<Week> weeks,Rankings rankings) {}
}
