package com.qs.ai.admian.util;

import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.dto.TextChunk;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for splitting clean text into RAG-friendly chunks.
 */
public final class TextChunkUtil {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final double DEFAULT_OVERLAP_RATIO = 0.10D;
    private static final int MIN_CHUNK_SIZE = 100;
    private static final int MAX_CHUNK_SIZE = 8000;
    private static final Pattern SEMANTIC_UNIT_PATTERN = Pattern.compile(
            "[^\\n。！？!?；;]+[。！？!?；;]?|\\n+"
    );

    private TextChunkUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static List<TextChunk> splitByFixedLength(Long fileId, String text) {
        return splitByFixedLength(fileId, text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_RATIO);
    }

    public static List<TextChunk> splitByFixedLength(Long fileId, String text, int chunkSize, double overlapRatio) {
        String cleanText = normalizeInput(text);
        if (!StringUtils.hasText(cleanText)) {
            return List.of();
        }
        validateOptions(fileId, chunkSize, overlapRatio);

        int overlapSize = calculateOverlapSize(chunkSize, overlapRatio);
        int stepSize = Math.max(1, chunkSize - overlapSize);
        List<TextChunk> chunks = new ArrayList<>();

        int chunkIndex = 0;
        int start = 0;
        while (start < cleanText.length()) {
            int preferredEnd = Math.min(start + chunkSize, cleanText.length());
            int end = findSafeCutPosition(cleanText, start, preferredEnd, chunkSize);
            if (end <= start) {
                end = preferredEnd;
            }

            String content = cleanText.substring(start, end).trim();
            if (StringUtils.hasText(content)) {
                chunks.add(buildChunk(fileId, chunkIndex++, content, start, end));
            }
            if (end >= cleanText.length()) {
                break;
            }

            int nextStart = Math.max(0, end - overlapSize);
            if (nextStart <= start) {
                nextStart = start + stepSize;
            }
            start = findSafeStartPosition(cleanText, Math.min(nextStart, cleanText.length() - 1), end);
        }

        return chunks;
    }

    public static List<TextChunk> splitBySemantic(Long fileId, String text) {
        return splitBySemantic(fileId, text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_RATIO);
    }

    public static List<TextChunk> splitBySemantic(Long fileId, String text, int chunkSize, double overlapRatio) {
        String cleanText = normalizeInput(text);
        if (!StringUtils.hasText(cleanText)) {
            return List.of();
        }
        validateOptions(fileId, chunkSize, overlapRatio);

        List<TextUnit> units = splitSemanticUnits(cleanText);
        if (units.isEmpty()) {
            return List.of();
        }

        int overlapSize = calculateOverlapSize(chunkSize, overlapRatio);
        List<TextChunk> chunks = new ArrayList<>();
        List<TextUnit> buffer = new ArrayList<>();
        int currentLength = 0;
        int chunkIndex = 0;

        for (TextUnit unit : units) {
            if (unit.text().length() > chunkSize) {
                if (!buffer.isEmpty()) {
                    chunks.add(buildChunkFromUnits(fileId, chunkIndex++, buffer));
                    buffer = buildOverlapUnits(buffer, overlapSize);
                    currentLength = sumLength(buffer);
                }
                List<TextChunk> fixedChunks = splitByFixedLength(fileId, unit.text(), chunkSize, overlapRatio);
                for (TextChunk chunk : fixedChunks) {
                    chunks.add(buildChunk(
                            fileId,
                            chunkIndex++,
                            chunk.content(),
                            unit.startOffset() + chunk.startOffset(),
                            unit.startOffset() + chunk.endOffset()
                    ));
                }
                buffer.clear();
                currentLength = 0;
                continue;
            }

            if (!buffer.isEmpty() && currentLength + unit.text().length() > chunkSize) {
                chunks.add(buildChunkFromUnits(fileId, chunkIndex++, buffer));
                buffer = buildOverlapUnits(buffer, overlapSize);
                currentLength = sumLength(buffer);
            }

            buffer.add(unit);
            currentLength += unit.text().length();
        }

        if (!buffer.isEmpty()) {
            chunks.add(buildChunkFromUnits(fileId, chunkIndex, buffer));
        }

        return chunks;
    }

    private static String normalizeInput(String text) {
        return TextParseUtil.cleanText(text);
    }

    private static void validateOptions(Long fileId, int chunkSize, double overlapRatio) {
        if (fileId == null) {
            throw new ParamException("fileId must not be null");
        }
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw new ParamException("chunkSize must be between 100 and 8000");
        }
        if (overlapRatio < 0 || overlapRatio >= 0.5D) {
            throw new ParamException("overlapRatio must be greater than or equal to 0 and less than 0.5");
        }
    }

    private static int calculateOverlapSize(int chunkSize, double overlapRatio) {
        return (int) Math.round(chunkSize * overlapRatio);
    }

    private static int findSafeCutPosition(String text, int start, int preferredEnd, int chunkSize) {
        if (preferredEnd >= text.length()) {
            return text.length();
        }

        int minEnd = start + Math.max(chunkSize / 2, chunkSize - calculateOverlapSize(chunkSize, 0.25D));
        for (int i = preferredEnd; i > Math.max(start, minEnd); i--) {
            char previous = text.charAt(i - 1);
            if (isSentenceBoundary(previous) || previous == '\n') {
                return i;
            }
        }

        for (int i = preferredEnd; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) {
                return i;
            }
        }
        return preferredEnd;
    }

    private static int findSafeStartPosition(String text, int candidateStart, int previousEnd) {
        int start = Math.max(0, Math.min(candidateStart, text.length()));
        while (start < previousEnd && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    private static List<TextUnit> splitSemanticUnits(String text) {
        List<TextUnit> units = new ArrayList<>();
        Matcher matcher = SEMANTIC_UNIT_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            String unitText = raw.trim();
            if (StringUtils.hasText(unitText)) {
                units.add(new TextUnit(unitText, matcher.start(), matcher.end()));
            }
        }
        return units;
    }

    private static List<TextUnit> buildOverlapUnits(List<TextUnit> units, int overlapSize) {
        if (overlapSize <= 0 || units.isEmpty()) {
            return new ArrayList<>();
        }

        List<TextUnit> overlapUnits = new ArrayList<>();
        int length = 0;
        for (int i = units.size() - 1; i >= 0; i--) {
            TextUnit unit = units.get(i);
            overlapUnits.add(0, unit);
            length += unit.text().length();
            if (length >= overlapSize) {
                break;
            }
        }
        return overlapUnits;
    }

    private static TextChunk buildChunkFromUnits(Long fileId, int chunkIndex, List<TextUnit> units) {
        StringBuilder content = new StringBuilder();
        for (TextUnit unit : units) {
            if (!content.isEmpty() && !startsWithPunctuation(unit.text())) {
                content.append('\n');
            }
            content.append(unit.text());
        }
        return buildChunk(
                fileId,
                chunkIndex,
                content.toString(),
                units.get(0).startOffset(),
                units.get(units.size() - 1).endOffset()
        );
    }

    private static TextChunk buildChunk(Long fileId, int chunkIndex, String content, int startOffset, int endOffset) {
        return new TextChunk(
                UUID.randomUUID().toString(),
                fileId,
                chunkIndex,
                content,
                startOffset,
                endOffset
        );
    }

    private static int sumLength(List<TextUnit> units) {
        return units.stream()
                .mapToInt(unit -> unit.text().length())
                .sum();
    }

    private static boolean isSentenceBoundary(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '；'
                || ch == '.' || ch == '!' || ch == '?' || ch == ';';
    }

    private static boolean startsWithPunctuation(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        char first = text.charAt(0);
        return first == '。' || first == '，' || first == '、' || first == ',' || first == '.';
    }

    private record TextUnit(String text, int startOffset, int endOffset) {
    }
}
