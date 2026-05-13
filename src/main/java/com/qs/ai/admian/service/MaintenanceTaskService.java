package com.qs.ai.admian.service;

import com.qs.ai.admian.config.AiModelConfigRegistry;
import com.qs.ai.admian.config.MaintenanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时维护任务：清理临时文件和预热缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceTaskService {

    private final MaintenanceProperties maintenanceProperties;
    private final AiModelConfigRegistry aiModelConfigRegistry;

    @Scheduled(cron = "${maintenance.cleanup.cron:0 0 3 * * ?}")
    public void cleanExpiredTempFiles() {
        MaintenanceProperties.Cleanup cleanup = maintenanceProperties.getCleanup();
        if (!Boolean.TRUE.equals(cleanup.getEnabled())) {
            return;
        }
        Path tempDir = Path.of(StringUtils.hasText(cleanup.getTempDir())
                        ? cleanup.getTempDir()
                        : "uploads/temp")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(tempDir)) {
            return;
        }

        int expiredHours = cleanup.getExpiredHours() == null || cleanup.getExpiredHours() <= 0
                ? 24
                : cleanup.getExpiredHours();
        Instant expireBefore = Instant.now().minus(Duration.ofHours(expiredHours));
        AtomicInteger deletedFiles = new AtomicInteger();
        AtomicInteger deletedDirs = new AtomicInteger();

        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !tempDir.equals(path))
                    .forEach(path -> deleteIfExpired(path, expireBefore, deletedFiles, deletedDirs));
            log.info("Expired temp cleanup completed, tempDir={}, deletedFiles={}, deletedDirs={}",
                    tempDir, deletedFiles.get(), deletedDirs.get());
        } catch (Exception ex) {
            log.error("Expired temp cleanup failed, tempDir={}", tempDir, ex);
        }
    }

    @Scheduled(cron = "${maintenance.cache-warmup.cron:0 */10 * * * ?}")
    public void warmupCaches() {
        MaintenanceProperties.CacheWarmup cacheWarmup = maintenanceProperties.getCacheWarmup();
        if (!Boolean.TRUE.equals(cacheWarmup.getEnabled())) {
            return;
        }
        try {
            aiModelConfigRegistry.warmupCache();
            log.debug("Cache warmup completed");
        } catch (Exception ex) {
            log.error("Cache warmup failed", ex);
        }
    }

    private void deleteIfExpired(Path path,
                                 Instant expireBefore,
                                 AtomicInteger deletedFiles,
                                 AtomicInteger deletedDirs) {
        try {
            if (Files.getLastModifiedTime(path).toInstant().isAfter(expireBefore)) {
                return;
            }
            if (Files.isDirectory(path)) {
                try (var children = Files.list(path)) {
                    if (children.findAny().isPresent()) {
                        return;
                    }
                }
                Files.deleteIfExists(path);
                deletedDirs.incrementAndGet();
                return;
            }
            Files.deleteIfExists(path);
            deletedFiles.incrementAndGet();
        } catch (IOException ex) {
            log.warn("Failed to delete expired temp path, path={}, message={}", path, ex.getMessage());
        }
    }
}
