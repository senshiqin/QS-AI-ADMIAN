package com.qs.ai.admian.service.impl;

import com.qs.ai.admian.config.RagProperties;
import com.qs.ai.admian.controller.request.RagAdvancedRetrieveRequest;
import com.qs.ai.admian.controller.request.RagEvalRequest;
import com.qs.ai.admian.controller.request.RagRewriteRequest;
import com.qs.ai.admian.controller.response.RagAdvancedRetrieveResponse;
import com.qs.ai.admian.controller.response.RagEvalResponse;
import com.qs.ai.admian.controller.response.RagRerankChunkResponse;
import com.qs.ai.admian.controller.response.RagRetrieveResponse;
import com.qs.ai.admian.controller.response.RagRetrievedChunkResponse;
import com.qs.ai.admian.controller.response.RagRewriteResponse;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.RagEnhanceService;
import com.qs.ai.admian.service.RagService;
import com.qs.ai.admian.service.dto.AiChatMessage;
import com.qs.ai.admian.service.dto.AiChatOptions;
import com.qs.ai.admian.util.MultiModelChatUtil;
import com.qs.ai.admian.util.TextParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Default RAG enhancement implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEnhanceServiceImpl implements RagEnhanceService {

    private static final double VECTOR_WEIGHT = 0.75D;
    private static final double KEYWORD_WEIGHT = 0.25D;

    private final RagService ragService;
    private final MultiModelChatUtil multiModelChatUtil;
    private final RagProperties ragProperties;

    @Override
    public RagRewriteResponse rewrite(RagRewriteRequest request) {
        String cleanQuery = cleanQuery(request == null ? null : request.queryText());
        boolean useLlm = request != null && Boolean.TRUE.equals(request.useLlm());
        String rewritten = useLlm
                ? rewriteByLlm(cleanQuery, request.provider(), request.model())
                : rewriteByRule(cleanQuery);
        if (!StringUtils.hasText(rewritten)) {
            rewritten = cleanQuery;
        }
        rewritten = TextParseUtil.cleanText(rewritten);
        return new RagRewriteResponse(
                cleanQuery,
                rewritten,
                !cleanQuery.equals(rewritten),
                useLlm,
                queryVariants(cleanQuery, rewritten)
        );
    }

    @Override
    public RagAdvancedRetrieveResponse advancedRetrieve(RagAdvancedRetrieveRequest request) {
        String originalQuery = cleanQuery(request == null ? null : request.queryText());
        int safeTopK = request == null || request.topK() == null || request.topK() <= 0
                ? defaultTopK()
                : request.topK();
        float safeMinScore = request == null || request.minScore() == null || request.minScore() <= 0
                ? defaultMinScore()
                : request.minScore();
        int candidateTopK = resolveCandidateTopK(request, safeTopK);

        boolean rewriteUsed = request == null || !Boolean.FALSE.equals(request.rewrite());
        String searchQuery = originalQuery;
        if (rewriteUsed) {
            RagRewriteResponse rewrite = rewrite(new RagRewriteRequest(
                    originalQuery,
                    request != null && Boolean.TRUE.equals(request.rewriteWithLlm()),
                    request == null ? null : request.provider(),
                    request == null ? null : request.model()
            ));
            searchQuery = rewrite.rewrittenQuery();
        }

        RagRetrieveResponse retrieval = ragService.retrieve(searchQuery, candidateTopK, safeMinScore);
        List<RagRerankChunkResponse> reranked = rerank(originalQuery, searchQuery, retrieval.chunks()).stream()
                .limit(safeTopK)
                .toList();
        return new RagAdvancedRetrieveResponse(
                originalQuery,
                searchQuery,
                rewriteUsed && !originalQuery.equals(searchQuery),
                safeTopK,
                candidateTopK,
                safeMinScore,
                reranked.size(),
                reranked,
                buildRagContext(reranked)
        );
    }

    @Override
    public RagEvalResponse evaluate(RagEvalRequest request) {
        if (request == null) {
            throw new ParamException("eval request must not be null");
        }
        RagAdvancedRetrieveResponse retrieval = advancedRetrieve(new RagAdvancedRetrieveRequest(
                request.queryText(),
                request.topK(),
                request.minScore(),
                request.rewrite(),
                request.rewriteWithLlm(),
                request.provider(),
                request.model(),
                null
        ));
        String merged = mergeContent(retrieval.chunks());
        List<String> expectedKeywords = request.expectedKeywords() == null
                ? List.of()
                : request.expectedKeywords().stream().filter(StringUtils::hasText).distinct().toList();
        List<String> matched = expectedKeywords.stream()
                .filter(StringUtils::hasText)
                .filter(keyword -> containsIgnoreCase(merged, keyword))
                .distinct()
                .toList();
        List<String> missing = expectedKeywords.stream()
                .filter(StringUtils::hasText)
                .filter(keyword -> !containsIgnoreCase(merged, keyword))
                .distinct()
                .toList();
        double recall = expectedKeywords.isEmpty() ? 1.0D : (double) matched.size() / expectedKeywords.size();
        boolean fileHit = !StringUtils.hasText(request.expectedFileName())
                || retrieval.chunks().stream()
                .anyMatch(chunk -> containsIgnoreCase(chunk.fileName(), request.expectedFileName()));
        boolean passed = recall >= 0.8D && fileHit && retrieval.hitCount() > 0;
        return new RagEvalResponse(
                retrieval.originalQuery(),
                retrieval.rewrittenQuery(),
                retrieval.topK(),
                retrieval.hitCount(),
                Math.round(recall * 10000D) / 10000D,
                fileHit,
                passed,
                matched,
                missing,
                retrieval.chunks()
        );
    }

    private String rewriteByRule(String query) {
        String rewritten = query
                .replace("qs ai", "qs-ai")
                .replace("QS AI", "QS-AI")
                .replace("admian", "admin")
                .replace("后台", "后台 管理")
                .replace("模型能力", "多模型能力 模型配置 模型路由");
        return TextParseUtil.cleanText(rewritten);
    }

    private String rewriteByLlm(String query, String provider, String model) {
        try {
            List<AiChatMessage> messages = List.of(
                    AiChatMessage.builder()
                            .role("system")
                            .content("Rewrite the user query for RAG retrieval. Return one concise query only.")
                            .build(),
                    AiChatMessage.builder()
                            .role("user")
                            .content(query)
                            .build()
            );
            String answer = multiModelChatUtil.chat(
                    provider,
                    messages,
                    AiChatOptions.builder()
                            .model(model)
                            .temperature(0.1D)
                            .maxTokens(128)
                            .maxInputTokens(1000)
                            .build()
            ).answer();
            return sanitizeLlmRewrite(answer, query);
        } catch (Exception ex) {
            log.warn("RAG query rewrite by LLM failed, fallback to rule rewrite", ex);
            return rewriteByRule(query);
        }
    }

    private String sanitizeLlmRewrite(String answer, String fallback) {
        if (!StringUtils.hasText(answer)) {
            return fallback;
        }
        String clean = answer
                .replace("```", "")
                .replace("\"", "")
                .trim();
        int lineBreak = clean.indexOf('\n');
        if (lineBreak >= 0) {
            clean = clean.substring(0, lineBreak).trim();
        }
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }

    private List<RagRerankChunkResponse> rerank(String originalQuery,
                                                String rewrittenQuery,
                                                List<RagRetrievedChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> toRerankChunk(originalQuery, rewrittenQuery, chunk))
                .sorted(Comparator.comparing(
                        RagRerankChunkResponse::rerankScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private RagRerankChunkResponse toRerankChunk(String originalQuery,
                                                 String rewrittenQuery,
                                                 RagRetrievedChunkResponse chunk) {
        double vectorScore = normalizeVectorScore(chunk.score());
        double keywordScore = keywordScore(originalQuery + " " + rewrittenQuery,
                nullToEmpty(chunk.fileName()) + " " + nullToEmpty(chunk.content()));
        double rerankScore = VECTOR_WEIGHT * vectorScore + KEYWORD_WEIGHT * keywordScore;
        return new RagRerankChunkResponse(
                chunk.chunkId(),
                chunk.fileId(),
                chunk.chunkIndex(),
                chunk.score(),
                round(keywordScore),
                round(rerankScore),
                chunk.content(),
                chunk.fileName(),
                chunk.fileType(),
                chunk.storagePath(),
                chunk.kbCode()
        );
    }

    private double normalizeVectorScore(Float score) {
        if (score == null) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, score));
    }

    private double keywordScore(String query, String content) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return 0D;
        }
        String lowerContent = nullToEmpty(content).toLowerCase(Locale.ROOT);
        long matched = queryTerms.stream()
                .filter(term -> lowerContent.contains(term.toLowerCase(Locale.ROOT)))
                .count();
        return (double) matched / queryTerms.size();
    }

    private Set<String> tokenize(String value) {
        String clean = TextParseUtil.cleanText(nullToEmpty(value))
                .replaceAll("[^\\p{IsHan}A-Za-z0-9_-]+", " ");
        String[] parts = clean.split("\\s+");
        Set<String> terms = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        addCjkBigrams(clean, terms);
        return terms;
    }

    private void addCjkBigrams(String clean, Set<String> terms) {
        List<Integer> cjkCodePoints = new ArrayList<>();
        clean.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .forEach(cjkCodePoints::add);
        for (int i = 0; i + 1 < cjkCodePoints.size(); i++) {
            terms.add(new String(Character.toChars(cjkCodePoints.get(i)))
                    + new String(Character.toChars(cjkCodePoints.get(i + 1))));
        }
    }

    private List<String> queryVariants(String originalQuery, String rewrittenQuery) {
        List<String> variants = new ArrayList<>();
        variants.add(originalQuery);
        if (!originalQuery.equals(rewrittenQuery)) {
            variants.add(rewrittenQuery);
        }
        return variants;
    }

    private String buildRagContext(List<RagRerankChunkResponse> chunks) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagRerankChunkResponse chunk = chunks.get(i);
            context.append("[")
                    .append(i + 1)
                    .append("] source: ")
                    .append(nullToEmpty(chunk.fileName()))
                    .append(" | fileId: ")
                    .append(chunk.fileId())
                    .append(" | chunk: ")
                    .append(chunk.chunkIndex())
                    .append(" | rerankScore: ")
                    .append(chunk.rerankScore())
                    .append(System.lineSeparator())
                    .append(nullToEmpty(chunk.content()))
                    .append(System.lineSeparator());
        }
        return context.toString();
    }

    private String mergeContent(List<RagRerankChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (RagRerankChunkResponse chunk : chunks) {
            builder.append(nullToEmpty(chunk.fileName()))
                    .append(' ')
                    .append(nullToEmpty(chunk.content()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String cleanQuery(String queryText) {
        String cleanQuery = TextParseUtil.cleanText(queryText);
        if (!StringUtils.hasText(cleanQuery)) {
            throw new ParamException("queryText must not be blank");
        }
        return cleanQuery;
    }

    private int resolveCandidateTopK(RagAdvancedRetrieveRequest request, int topK) {
        if (request != null && request.candidateTopK() != null && request.candidateTopK() >= topK) {
            return request.candidateTopK();
        }
        return topK * defaultCandidateMultiplier();
    }

    private int defaultTopK() {
        return ragProperties.getDefaultTopK() == null || ragProperties.getDefaultTopK() <= 0
                ? 5
                : ragProperties.getDefaultTopK();
    }

    private float defaultMinScore() {
        return ragProperties.getDefaultMinScore() == null || ragProperties.getDefaultMinScore() <= 0
                ? 0.55F
                : ragProperties.getDefaultMinScore();
    }

    private int defaultCandidateMultiplier() {
        return ragProperties.getCandidateMultiplier() == null || ragProperties.getCandidateMultiplier() <= 0
                ? 3
                : ragProperties.getCandidateMultiplier();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(keyword)
                && source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double round(double value) {
        return Math.round(value * 10000D) / 10000D;
    }
}
