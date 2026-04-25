package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.request.ChatPointsTxRequest;
import com.qs.ai.admian.controller.response.ChatPointsTxResponse;

/**
 * Transaction demo service for chat + points update.
 */
public interface ChatPointsTxService {

    ChatPointsTxResponse saveChatAndUpdatePoints(ChatPointsTxRequest request);
}
