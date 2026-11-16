package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import java.math.*;
import java.util.*;
import org.springframework.http.HttpStatus;

/** The same immutable batch standard governs first submission and corrections. */
final class ScoreCalculation {
    private ScoreCalculation() {}
    record Result(BigDecimal total, Map<Long,Integer> values) {}
    static Result calculate(List<SchemeViews.Dimension> dimensions,List<ScoreRequests.Dimension> input) {
        var supplied=new HashMap<Long,Integer>();
        for(var dimension:input) {
            if(supplied.put(dimension.dimensionId(),dimension.score())!=null) throw invalidDimensions();
        }
        if(supplied.size()!=dimensions.size() || dimensions.stream().anyMatch(d->!supplied.containsKey(d.id()))) throw invalidDimensions();
        var total=BigDecimal.ZERO;
        for(var dimension:dimensions) total=total.add(dimension.weight().multiply(BigDecimal.valueOf(supplied.get(dimension.id()))));
        return new Result(total.divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP),supplied);
    }
    private static ApiException invalidDimensions() { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DIMENSIONS","必须完整提交本批次方案的全部维度，不能重复或添加其他维度"); }
}
