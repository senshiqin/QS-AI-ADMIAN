package com.qs.ai.admian.config;

import com.qs.ai.admian.exception.BusinessException;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Global exception handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validation error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), message);
        return ApiResponse.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Bind error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), message);
        return ApiResponse.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException ex,
                                                                HttpServletRequest request) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(item -> item.getPropertyPath() + ": " + item.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), message);
        return ApiResponse.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleRequestParamException(Exception ex, HttpServletRequest request) {
        log.warn("Request parameter error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return ApiResponse.fail(ResultCode.BAD_REQUEST.getCode(), "Request parameter error: " + ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.error("Business exception, uri={}, method={}, code={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getCode(), ex.getMessage(), ex);
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public ApiResponse<Void> handleNullPointerException(NullPointerException ex, HttpServletRequest request) {
        log.error("NullPointerException, uri={}, method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return ApiResponse.fail(ResultCode.NULL_POINTER_ERROR.getCode(), ResultCode.NULL_POINTER_ERROR.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception, uri={}, method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return ApiResponse.fail(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
