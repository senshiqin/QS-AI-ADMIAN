package com.qs.ai.admian.exception;

import com.qs.ai.admian.util.response.ResultCode;

/**
 * Custom parameter exception.
 */
public class ParamException extends BusinessException {

    public ParamException(String message) {
        super(ResultCode.PARAM_INVALID, message);
    }

    public ParamException() {
        super(ResultCode.PARAM_INVALID);
    }
}
