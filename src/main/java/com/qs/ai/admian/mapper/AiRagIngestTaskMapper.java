package com.qs.ai.admian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qs.ai.admian.entity.AiRagIngestTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for RAG ingest task status.
 */
@Mapper
public interface AiRagIngestTaskMapper extends BaseMapper<AiRagIngestTask> {
}
