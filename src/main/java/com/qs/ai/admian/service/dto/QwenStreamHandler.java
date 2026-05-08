package com.qs.ai.admian.service.dto;

import java.io.IOException;

/**
 * Receives streamed Qwen response fragments.
 */
@FunctionalInterface
public interface QwenStreamHandler {

    void onContent(String content) throws IOException;
}
