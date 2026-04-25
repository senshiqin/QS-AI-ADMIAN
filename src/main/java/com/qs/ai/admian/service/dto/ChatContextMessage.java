package com.qs.ai.admian.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat context message cached in Redis list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatContextMessage {

    private String role;
    private String content;
    private LocalDateTime timestamp;
}
