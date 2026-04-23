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
 * AI知识库文件表实体（ai_knowledge_file）。
 */
@Data
@TableName("ai_knowledge_file")
public class AiKnowledgeFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库文件主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 知识库编码 */
    @TableField("kb_code")
    private String kbCode;

    /** 文件名 */
    @TableField("file_name")
    private String fileName;

    /** 文件类型(pdf/docx/txt/md等) */
    @TableField("file_type")
    private String fileType;

    /** 文件大小(字节) */
    @TableField("file_size")
    private Long fileSize;

    /** 存储路径(对象存储或本地路径) */
    @TableField("storage_path")
    private String storagePath;

    /** 文件哈希(SHA-256) */
    @TableField("file_hash")
    private String fileHash;

    /** 解析状态:0待处理,1处理中,2成功,3失败 */
    @TableField("parse_status")
    private Integer parseStatus;

    /** 切片数量 */
    @TableField("chunk_count")
    private Integer chunkCount;

    /** 向量模型名称 */
    @TableField("embedding_model")
    private String embeddingModel;

    /** 向量索引名称 */
    @TableField("vector_index_name")
    private String vectorIndexName;

    /** 最近解析时间 */
    @TableField("last_parse_time")
    private LocalDateTime lastParseTime;

    /** 上传人ID(sys_user.id) */
    @TableField("uploader_user_id")
    private Long uploaderUserId;

    /** 备注 */
    @TableField("remark")
    private String remark;

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
