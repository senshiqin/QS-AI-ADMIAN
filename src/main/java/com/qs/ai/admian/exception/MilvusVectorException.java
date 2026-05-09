package com.qs.ai.admian.exception;

import com.qs.ai.admian.util.response.ResultCode;

/**
 * Milvus vector storage exception.
 */
public class MilvusVectorException extends BusinessException {

    public MilvusVectorException(String message) {
        super(ResultCode.VECTOR_STORE_ERROR, message);
    }
}
