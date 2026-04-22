package com.qs.ai.admian.exception;

import com.qs.ai.admian.util.response.ResultCode;

/**
 * AI API call exception.
 */
public class AiApiException extends BusinessException {

    public AiApiException(String message) {
        super(ResultCode.AI_API_CALL_FAILED, message);
    }

    public AiApiException() {
        super(ResultCode.AI_API_CALL_FAILED);
    }
}
