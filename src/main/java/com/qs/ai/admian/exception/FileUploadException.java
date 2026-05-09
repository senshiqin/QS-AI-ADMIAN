package com.qs.ai.admian.exception;

import com.qs.ai.admian.util.response.ResultCode;

/**
 * File upload business exception.
 */
public class FileUploadException extends BusinessException {

    public FileUploadException(String message) {
        super(ResultCode.FILE_UPLOAD_ERROR, message);
    }

    public FileUploadException() {
        super(ResultCode.FILE_UPLOAD_ERROR);
    }
}
