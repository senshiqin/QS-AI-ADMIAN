package com.qs.ai.admian.util.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;

/**
 * Unified response body.
 *
 * @param <T> data type
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success() {
        return of(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return of(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return of(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> ApiResponse<T> fail() {
        return of(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMessage(), null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return of(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return of(code, message, null);
    }

    public static <T> ApiResponse<T> fail(ResultCode resultCode) {
        return of(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return of(code, message, data);
    }

    private static <T> ApiResponse<T> of(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, MDC.get("traceId"), LocalDateTime.now());
    }
}
