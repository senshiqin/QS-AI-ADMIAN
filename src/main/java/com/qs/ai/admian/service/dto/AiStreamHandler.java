package com.qs.ai.admian.service.dto;

import java.io.IOException;

/**
 * Receives streamed AI response fragments.
 */
@FunctionalInterface
public interface AiStreamHandler {

    void onContent(String content) throws IOException;
}
