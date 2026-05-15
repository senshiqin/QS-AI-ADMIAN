package com.qs.ai.admian.metrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qs.ai.admian.entity.AiRagIngestTask;
import com.qs.ai.admian.service.AiRagIngestTaskService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gauges for current RAG ingest task status distribution.
 */
@Component
@RequiredArgsConstructor
public class RagTaskMetricsBinder {

    private final MeterRegistry meterRegistry;
    private final AiRagIngestTaskService taskService;

    @PostConstruct
    public void bind() {
        bindStatusGauge(AiRagIngestTaskService.STATUS_PENDING);
        bindStatusGauge(AiRagIngestTaskService.STATUS_RUNNING);
        bindStatusGauge(AiRagIngestTaskService.STATUS_SUCCESS);
        bindStatusGauge(AiRagIngestTaskService.STATUS_FAILED);
    }

    private void bindStatusGauge(String status) {
        Gauge.builder("qs.rag.ingest.tasks.current", taskService, service -> service.count(
                        new LambdaQueryWrapper<AiRagIngestTask>().eq(AiRagIngestTask::getStatus, status)
                ))
                .description("Current RAG ingest task count by status")
                .tag("status", status.toLowerCase())
                .register(meterRegistry);
    }
}
