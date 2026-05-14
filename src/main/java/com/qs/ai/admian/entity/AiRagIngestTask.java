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
 * RAG file ingestion task status.
 */
@Data
@TableName("ai_rag_ingest_task")
public class AiRagIngestTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("knowledge_file_id")
    private Long knowledgeFileId;

    @TableField("kb_code")
    private String kbCode;

    @TableField("file_name")
    private String fileName;

    @TableField("storage_path")
    private String storagePath;

    @TableField("status")
    private String status;

    @TableField("progress_percent")
    private Integer progressPercent;

    @TableField("current_step")
    private String currentStep;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retry")
    private Integer maxRetry;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("overlap_ratio")
    private Double overlapRatio;

    @TableField("text_length")
    private Integer textLength;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("stored_vector_count")
    private Long storedVectorCount;

    @TableField("embedding_model")
    private String embeddingModel;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
