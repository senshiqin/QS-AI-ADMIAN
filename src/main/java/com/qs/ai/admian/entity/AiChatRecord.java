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
 * AI对话记录表实体（ai_chat_record）。
 */
@Data
@TableName("ai_chat_record")
public class AiChatRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 对话记录主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    @TableField("conversation_id")
    private String conversationId;

    /** 用户ID(sys_user.id) */
    @TableField("user_id")
    private Long userId;

    /** 角色类型:user/assistant/system/tool */
    @TableField("role_type")
    private String roleType;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 模型名称(deepseek/tongyi等) */
    @TableField("model_name")
    private String modelName;

    /** 输入token数 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 输出token数 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 总token数 */
    @TableField("total_tokens")
    private Integer totalTokens;

    /** 响应耗时毫秒 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** AI平台请求ID */
    @TableField("request_id")
    private String requestId;

    /** 命中文件ID(ai_knowledge_file.id) */
    @TableField("knowledge_file_id")
    private Long knowledgeFileId;

    /** 错误码 */
    @TableField("error_code")
    private String errorCode;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 扩展字段(JSON) */
    @TableField("ext_json")
    private String extJson;

    /** 逻辑删除:0未删除,1已删除 */
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
