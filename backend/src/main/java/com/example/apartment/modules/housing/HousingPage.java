package com.example.apartment.modules.housing;

import java.util.List;
import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;

public record HousingPage<T>(List<T> items, long total, int page, int size) {
    static String validate(String q, int page, int size) {
        if (q.length() > 64 || page < 1 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "关键词最多 64 字，页码至少为 1，每页 1–100 条");
        }
        return q.strip();
    }
}
