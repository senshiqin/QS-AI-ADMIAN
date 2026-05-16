package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.request.SpringAiChatRequest;
import com.qs.ai.admian.controller.response.SpringAiChatResponse;
import com.qs.ai.admian.controller.response.SpringAiCompareResponse;

/**
 * Spring AI comparison and adapter demo service.
 */
public interface SpringAiCompareService {

    SpringAiCompareResponse compare();

    SpringAiChatResponse chat(SpringAiChatRequest request);
}
