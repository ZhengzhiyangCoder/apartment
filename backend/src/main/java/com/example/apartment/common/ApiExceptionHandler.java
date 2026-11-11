package com.example.apartment.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> business(ApiException error) {
        return ResponseEntity.status(error.status()).body(new ApiError(error.code(), error.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> invalidInput(Exception error) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "输入格式不正确，请检查必填项及长度"));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", "没有访问该内容的权限"));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "请求的内容不存在"));
    }
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiError> conflict(ConcurrencyFailureException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONCURRENT_CHANGE", "账号正在被其他操作更新，请刷新后重试"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception error) {
        // SQL/validation exception messages can include bound values; never log credential-bearing payloads.
        log.error("Unexpected request failure ({})", error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError("SERVER_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
