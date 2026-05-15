package com.qs.ai.admian.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Centralized business metrics recorder for AI and RAG workflows.
 */
@Component
@RequiredArgsConstructor
public class AiMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public void recordChat(String provider, String mode, boolean success, long durationMs) {
        Counter.builder("qs.ai.chat.requests")
                .description("AI chat request count")
                .tag("provider", safe(provider))
                .tag("mode", safe(mode))
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
        Timer.builder("qs.ai.chat.duration")
                .description("AI chat request duration")
                .tag("provider", safe(provider))
                .tag("mode", safe(mode))
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
    }

    public void recordRagRetrieve(boolean success, boolean cacheHit, int hitCount, long durationMs) {
        Counter.builder("qs.rag.retrieve.requests")
                .description("RAG retrieve request count")
                .tag("success", String.valueOf(success))
                .tag("cache_hit", String.valueOf(cacheHit))
                .register(meterRegistry)
                .increment();
        Timer.builder("qs.rag.retrieve.duration")
                .description("RAG retrieve duration")
                .tag("success", String.valueOf(success))
                .tag("cache_hit", String.valueOf(cacheHit))
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
        meterRegistry.counter("qs.rag.retrieve.hits.total")
                .increment(Math.max(hitCount, 0));
    }

    public void recordRagIngest(String status, long durationMs, int chunkCount, long vectorCount) {
        Counter.builder("qs.rag.ingest.tasks")
                .description("RAG ingest task count")
                .tag("status", safe(status))
                .register(meterRegistry)
                .increment();
        Timer.builder("qs.rag.ingest.duration")
                .description("RAG ingest task duration")
                .tag("status", safe(status))
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
        meterRegistry.counter("qs.rag.ingest.chunks.total")
                .increment(Math.max(chunkCount, 0));
        meterRegistry.counter("qs.rag.ingest.vectors.total")
                .increment(Math.max(vectorCount, 0));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase();
    }
}
