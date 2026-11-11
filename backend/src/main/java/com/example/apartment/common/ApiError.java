package com.example.apartment.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public record ApiError(String code, String message) {
    public static void write(ObjectMapper json, HttpServletResponse response, int status,
                             String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), new ApiError(code, message));
    }
}
