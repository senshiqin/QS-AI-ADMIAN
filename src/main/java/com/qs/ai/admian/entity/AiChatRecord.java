package com.qs.ai.admian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI chat record entity (ai_chat_record).
 */
@Data
@TableName("ai_chat_record")
public class AiChatRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("user_id")
    private Long userId;

    @TableField("role_type")
    private String roleType;

    @TableField("chat_time")
    private LocalDateTime chatTime;

    @TableField("content")
    private String content;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("request_id")
    private String requestId;

    @TableField("knowledge_file_id")
    private Long knowledgeFileId;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("ext_json")
    private String extJson;

    @TableField("deleted")
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}