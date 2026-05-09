package com.qs.ai.admian.util.response;

/**
 * Common response codes.
 */
public enum ResultCode {

    SUCCESS(200, "Success"),
    BAD_REQUEST(400, "Bad request"),
    PARAM_INVALID(4001, "Parameter validation failed"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error"),
    NULL_POINTER_ERROR(5001, "Null pointer error"),
    FILE_UPLOAD_ERROR(5200, "File upload error"),
    TEXT_PARSE_ERROR(5201, "Text parse error"),
    AI_SERVICE_ERROR(5100, "AI service error"),
    AI_KNOWLEDGE_EMPTY(5101, "Knowledge base no result"),
    AI_API_CALL_FAILED(5102, "AI API call failed");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
