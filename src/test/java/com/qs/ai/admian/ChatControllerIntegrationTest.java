package com.qs.ai.admian;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.exception.AiApiException;
import com.qs.ai.admian.mapper.AiChatRecordMapper;
import com.qs.ai.admian.service.ChatContextService;
import com.qs.ai.admian.service.QwenChatService;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.ChatContextMessage;
import com.qs.ai.admian.service.dto.QwenChatResult;
import com.qs.ai.admian.util.JwtUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers chat API integration with context cache and chat record persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

    private static final String JWT_SECRET = "ReplaceWithAtLeast32BytesSecretKeyForJwtAuth123456";
    private static final String TOKEN = "Bearer " + JwtUtil.generateToken("1", "tester", JWT_SECRET);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiChatRecordMapper aiChatRecordMapper;

    @MockBean
    private QwenChatService qwenChatService;

    @MockBean
    private ChatContextService chatContextService;

    @Test
    void sendMessageShouldUseContextSaveRecordsAndRefreshContext() throws Exception {
        String conversationId = "conv-send-context";
        when(chatContextService.getContextMessages(1L, conversationId)).thenReturn(List.of(
                ChatContextMessage.builder().role("user").content("previous question").build(),
                ChatContextMessage.builder().role("assistant").content("previous answer").build()
        ));
        when(qwenChatService.chat(anyString(), anyList(), anyDouble())).thenReturn(QwenChatResult.builder()
                .answer("current answer")
                .requestId("req-send-001")
                .promptTokens(10)
                .completionTokens(5)
                .totalTokens(15)
                .build());

        mockMvc.perform(post("/api/v1/ai/chat/send")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-send-context",
                                  "model": "qwen-turbo",
                                  "temperature": 0.7,
                                  "messages": [
                                    {"role": "user", "content": "current question"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("current answer"))
                .andExpect(jsonPath("$.data.totalTokens").value(15));

        ArgumentCaptor<List<AiChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(qwenChatService).chat(eq("qwen-turbo"), messagesCaptor.capture(), eq(0.7D));
        List<AiChatMessage> sentMessages = messagesCaptor.getValue();
        Assertions.assertEquals(3, sentMessages.size());
        Assertions.assertEquals("previous question", sentMessages.get(0).getContent());
        Assertions.assertEquals("previous answer", sentMessages.get(1).getContent());
        Assertions.assertEquals("current question", sentMessages.get(2).getContent());

        List<AiChatRecord> records = selectRecords(conversationId);
        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals("user", records.get(0).getRoleType());
        Assertions.assertEquals("current question", records.get(0).getContent());
        Assertions.assertEquals("assistant", records.get(1).getRoleType());
        Assertions.assertEquals("current answer", records.get(1).getContent());

        verify(chatContextService).addContextMessage(1L, conversationId, "user", "current question");
        verify(chatContextService).addContextMessage(1L, conversationId, "assistant", "current answer");
    }

    @Test
    void sendMessageShouldPersistErrorRecordAndReturnUnifiedError() throws Exception {
        String conversationId = "conv-send-error";
        when(chatContextService.getContextMessages(1L, conversationId)).thenReturn(List.of());
        when(qwenChatService.chat(anyString(), anyList(), anyDouble()))
                .thenThrow(new AiApiException("AI service unavailable"));

        mockMvc.perform(post("/api/v1/ai/chat/send")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-send-error",
                                  "messages": [
                                    {"role": "user", "content": "will fail"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5102))
                .andExpect(jsonPath("$.message").value("AI service unavailable"));

        List<AiChatRecord> records = selectRecords(conversationId);
        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals("will fail", records.get(0).getContent());
        Assertions.assertEquals("assistant", records.get(1).getRoleType());
        Assertions.assertEquals("AI service unavailable", records.get(1).getErrorMessage());
        verify(chatContextService, never()).addContextMessage(eq(1L), eq(conversationId), anyString(), anyString());
    }

    @Test
    void streamMessageShouldPushSseSaveRecordsAndRefreshContext() throws Exception {
        String conversationId = "conv-stream-context";
        when(chatContextService.getContextMessages(1L, conversationId)).thenReturn(List.of(
                ChatContextMessage.builder().role("assistant").content("cached answer").build()
        ));
        when(qwenChatService.streamChat(anyString(), anyList(), anyDouble(), any())).thenAnswer(invocation -> {
            invocation.<com.qs.ai.admian.service.dto.QwenStreamHandler>getArgument(3).onContent("OK");
            return QwenChatResult.builder()
                    .answer("OK")
                    .requestId("req-stream-001")
                    .promptTokens(8)
                    .completionTokens(2)
                    .totalTokens(10)
                    .build();
        });

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "conversationId": "conv-stream-context",
                                  "messages": [
                                    {"role": "user", "content": "stream question"}
                                  ]
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = dispatchedResult.getResponse().getContentAsString();
        Assertions.assertTrue(responseBody.contains("event:message"));
        Assertions.assertTrue(responseBody.contains("data:O"));
        Assertions.assertTrue(responseBody.contains("data:K"));
        Assertions.assertTrue(responseBody.contains("event:done"));

        List<AiChatRecord> records = selectRecords(conversationId);
        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals("stream question", records.get(0).getContent());
        Assertions.assertEquals("OK", records.get(1).getContent());
        verify(chatContextService).addContextMessage(1L, conversationId, "user", "stream question");
        verify(chatContextService).addContextMessage(1L, conversationId, "assistant", "OK");
    }

    @Test
    void invalidRequestShouldReturnParamErrorBeforeCallingAiApi() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat/send")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-invalid",
                                  "messages": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));

        verify(qwenChatService, never()).chat(anyString(), anyList(), anyDouble());
    }

    private List<AiChatRecord> selectRecords(String conversationId) {
        return aiChatRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiChatRecord>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("id")
        );
    }
}
