package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.modules.identity.Role;
import com.example.apartment.modules.housing.InspectionHousingQuery;
import com.example.apartment.modules.hygiene.ResultViews.Period;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.hygiene.StatisticsViews.*;

@Service
public class StatisticsService {
    private final StatisticsMapper statistics;
    private final Clock clock;
    private final InspectionHousingQuery housing;
    private final ResultDetailsQuery details;
    public StatisticsService(StatisticsMapper statistics,Clock clock,InspectionHousingQuery housing,ResultDetailsQuery details) {
        this.statistics=statistics;this.clock=clock;this.housing=housing;this.details=details;
    }
    @Transactional(readOnly=true)
    public Statistics results(AccountPrincipal actor,ResultViews.Kind kind,LocalDate date,Long buildingId,Long roomId,int page,int size) {
        if(actor.role()!=Role.SYSTEM_ADMIN && actor.role()!=Role.DORM_MANAGER)
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","没有查看管理统计的权限");
        if(date==null) date=LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        if(date.getYear()<1901 || date.getYear()>9998) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PERIOD","日期年份须在1901至9998之间");
        if(page<1 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码须为正数，每页1至100项");
        var scope=housing.resultScope(buildingId,roomId);
        buildingId=scope.buildingId();
        var period=Period.at(kind,date);
        var total=statistics.summary(buildingId,roomId,period.start(),period.end());
        var buildings=new ArrayList<Building>();
        if(roomId==null) {
            statistics.buildings(buildingId,period.start(),period.end()).forEach(r->buildings.add(
                    new Building(r.buildingId(),r.buildingName(),r.score(),r.participants(),r.samples(),Progress.of(r.expected(),r.samples()))));
            if(buildings.isEmpty() && buildingId!=null) buildings.add(new Building(buildingId,scope.buildingName(),null,0,0,Progress.of(0,0)));
        }
        var rankings=statistics.rankings(buildingId,roomId,period.start(),period.end(),size,((long)page-1)*size);
        return new Statistics(scope,period,new Summary(total.participants(),total.samples(),Progress.of(total.expected(),total.samples())),
                buildings,trend(scope,period,buildings),new Rankings(rankings,total.participants(),page,size),dimensions(scope,period,buildings),
                roomId==null?List.of():details.weeks(roomId,period));
    }
    private List<Trend> trend(InspectionHousingQuery.ResultScope scope,Period selected,List<Building> buildings) {
        var start=selected.kind()==ResultViews.Kind.WEEK?selected.start().minusWeeks(11):selected.start().minusMonths(11);
        var rows=statistics.trend(scope.buildingId(),scope.roomId(),selected.kind().name(),start,selected.end());
        var names=new TreeMap<Long,String>();var series=new HashMap<Long,Map<LocalDate,StatisticsMapper.TrendRow>>();
        for(var row:rows) { names.put(row.buildingId(),row.buildingName());series.computeIfAbsent(row.buildingId(),key->new HashMap<>()).put(row.start(),row); }
        for(var building:buildings) names.put(building.buildingId(),building.buildingName());
        if(scope.buildingId()!=null) names.put(scope.buildingId(),scope.buildingName());
        var result=new ArrayList<Trend>();
        for(var entry:names.entrySet()) {
            var points=new ArrayList<Point>();
            for(int i=0;i<12;i++) {
                var period=Period.at(selected.kind(),selected.kind()==ResultViews.Kind.WEEK?start.plusWeeks(i):start.plusMonths(i));
                var row=series.getOrDefault(entry.getKey(),Map.of()).get(period.start());
                points.add(new Point(period,row==null?null:row.score(),row==null?0:row.participants(),row==null?0:row.samples()));
            }
            result.add(new Trend(entry.getKey(),entry.getValue(),scope.roomId(),scope.roomNo(),points));
        }
        return result;
    }
    private List<Dimensions> dimensions(InspectionHousingQuery.ResultScope scope,Period period,List<Building> buildings) {
        record Group(long schemeId,long buildingId) {}
        var groups=new LinkedHashMap<Group,Dimensions>();
        var names=new HashMap<Long,String>();
        for(var building:buildings) names.put(building.buildingId(),building.buildingName());
        if(scope.roomId()!=null) names.put(scope.buildingId(),scope.buildingName());
        for(var row:statistics.dimensions(scope.buildingId(),scope.roomId(),period.start(),period.end())) {
            var group=groups.computeIfAbsent(new Group(row.schemeId(),row.buildingId()),key->new Dimensions(
                    row.schemeId(),row.schemeName(),row.buildingId(),names.getOrDefault(row.buildingId(),row.buildingName()),new ArrayList<>()));
            group.dimensions().add(new Dimension(row.dimensionId(),row.name(),row.weight(),row.score(),row.samples()));
        }
        return List.copyOf(groups.values());
    }
}
