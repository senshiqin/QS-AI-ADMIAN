package com.qs.ai.admian.config;

import com.qs.ai.admian.exception.BusinessException;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.util.response.ApiResponse;
import com.qs.ai.admian.util.response.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Global exception handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                                  HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validation error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), message);
        return fail(HttpStatus.BAD_REQUEST, ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Bind error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), message);
        return fail(HttpStatus.BAD_REQUEST, ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex,
                                                                               HttpServletRequest request) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(item -> item.getPropertyPath() + ": " + item.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), message);
        return fail(HttpStatus.BAD_REQUEST, ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestParamException(Exception ex, HttpServletRequest request) {
        log.warn("Request parameter error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return fail(HttpStatus.BAD_REQUEST, ResultCode.BAD_REQUEST.getCode(), "Request parameter error: " + ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                                 HttpServletRequest request) {
        log.warn("Upload size exceeded, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return fail(HttpStatus.BAD_REQUEST, ResultCode.PARAM_INVALID.getCode(), "file size must be less than or equal to 100MB");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException ex, HttpServletRequest request) {
        log.warn("Multipart upload error, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return fail(HttpStatus.BAD_REQUEST, ResultCode.BAD_REQUEST.getCode(), "Multipart upload error: " + ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest request) {
        log.warn("Method not allowed, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return fail(HttpStatus.METHOD_NOT_ALLOWED, ResultCode.METHOD_NOT_ALLOWED.getCode(), ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                         HttpServletRequest request) {
        log.warn("Unsupported media type, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ResultCode.UNSUPPORTED_MEDIA_TYPE.getCode(), ex.getMessage());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex, HttpServletRequest request) {
        log.warn("Resource not found, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage());
        return fail(HttpStatus.NOT_FOUND, ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(AiApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiApiException(AiApiException ex, HttpServletRequest request) {
        log.error("AI API exception, uri={}, method={}, code={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getCode(), ex.getMessage(), ex);
        return fail(HttpStatus.OK, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.error("Business exception, uri={}, method={}, code={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getCode(), ex.getMessage(), ex);
        return fail(HttpStatus.OK, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ApiResponse<Void>> handleRedisException(RuntimeException ex, HttpServletRequest request) {
        log.error("Redis exception, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage(), ex);
        return fail(HttpStatus.OK, ResultCode.REDIS_ERROR.getCode(), ResultCode.REDIS_ERROR.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException ex,
                                                                       HttpServletRequest request) {
        log.error("Database exception, uri={}, method={}, message={}",
                request.getRequestURI(), request.getMethod(), ex.getMessage(), ex);
        return fail(HttpStatus.OK, ResultCode.DATABASE_ERROR.getCode(), ResultCode.DATABASE_ERROR.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNullPointerException(NullPointerException ex, HttpServletRequest request) {
        log.error("NullPointerException, uri={}, method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return fail(HttpStatus.OK, ResultCode.NULL_POINTER_ERROR.getCode(), ResultCode.NULL_POINTER_ERROR.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception, uri={}, method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return fail(HttpStatus.OK, ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message));
    }
}
