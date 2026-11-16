package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class StatisticsViews {
    private StatisticsViews() {}
    public record Progress(long expected,long inspected,long missing,BigDecimal completionRate) {
        static Progress of(long expected,long inspected) {
            return new Progress(expected,inspected,expected-inspected,expected==0?null:
                    BigDecimal.valueOf(inspected).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(expected),2,RoundingMode.HALF_UP));
        }
    }
    public record Building(long buildingId,String buildingName,BigDecimal score,long participants,long samples,Progress progress) {}
    public record Summary(long participants,long samples,Progress progress) {}
    public record Ranking(long roomId,long buildingId,String buildingName,String roomNo,BigDecimal score,long samples,long rank) {}
    public record Rankings(List<Ranking> items,long total,int page,int size) {}
    public record Point(ResultViews.Period period,BigDecimal score,long participants,long samples) {}
    public record Trend(long buildingId,String buildingName,Long roomId,String roomNo,List<Point> points) {}
    public record Dimension(long dimensionId,String name,BigDecimal weight,BigDecimal score,long samples) {}
    public record Dimensions(long schemeId,String schemeName,long buildingId,String buildingName,List<Dimension> dimensions) {}
    public record Statistics(com.example.apartment.modules.housing.InspectionHousingQuery.ResultScope scope,
            ResultViews.Period period,Summary summary,List<Building> buildings,List<Trend> trend,Rankings rankings,
            List<Dimensions> dimensions,List<ResultViews.Week> weeks) {}
}
