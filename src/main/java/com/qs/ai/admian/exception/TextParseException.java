package com.qs.ai.admian.exception;

import com.qs.ai.admian.util.response.ResultCode;

/**
 * Text parsing exception for uploaded documents.
 */
public class TextParseException extends BusinessException {

    public TextParseException(String message) {
        super(ResultCode.TEXT_PARSE_ERROR, message);
    }

    public TextParseException() {
        super(ResultCode.TEXT_PARSE_ERROR);
    }
}
