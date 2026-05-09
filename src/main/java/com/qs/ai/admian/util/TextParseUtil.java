package com.qs.ai.admian.util;

import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.exception.TextParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility for parsing document files into clean plain text.
 */
public final class TextParseUtil {

    private static final Pattern HORIZONTAL_BLANKS = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern MULTIPLE_NEW_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern LINE_EDGE_BLANKS = Pattern.compile("(?m)^\\s+|\\s+$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

    private TextParseUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String parse(Path filePath) {
        if (filePath == null) {
            throw new ParamException("filePath must not be null");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ParamException("file does not exist or is not a regular file");
        }

        String extension = getExtension(filePath.getFileName().toString());
        try {
            return switch (extension) {
                case "pdf" -> parsePdf(filePath);
                case "docx" -> parseDocx(filePath);
                case "txt" -> parseTxt(filePath);
                default -> throw new ParamException("unsupported text parse file type: " + extension);
            };
        } catch (IOException ex) {
            throw new TextParseException("Failed to parse " + extension + " file");
        }
    }

    public static String parse(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new ParamException("filePath must not be blank");
        }
        return parse(Path.of(filePath));
    }

    public static String parse(InputStream inputStream, String filename) {
        if (inputStream == null) {
            throw new ParamException("inputStream must not be null");
        }

        String extension = getExtension(filename);
        try {
            return switch (extension) {
                case "pdf" -> parsePdf(inputStream);
                case "docx" -> parseDocx(inputStream);
                case "txt" -> parseTxt(inputStream);
                default -> throw new ParamException("unsupported text parse file type: " + extension);
            };
        } catch (IOException ex) {
            throw new TextParseException("Failed to parse " + extension + " file");
        }
    }

    public static String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String normalized = text
                .replace('\uFEFF', ' ')
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        normalized = CONTROL_CHARS.matcher(normalized).replaceAll("");
        normalized = HORIZONTAL_BLANKS.matcher(normalized).replaceAll(" ");
        normalized = LINE_EDGE_BLANKS.matcher(normalized).replaceAll("");
        normalized = MULTIPLE_NEW_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.trim();
    }

    private static String parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(false);
            return cleanText(stripper.getText(document));
        }
    }

    private static String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(false);
            return cleanText(stripper.getText(document));
        }
    }

    private static String parseDocx(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return parseDocx(inputStream);
        }
    }

    private static String parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return cleanText(extractor.getText());
        }
    }

    private static String parseTxt(Path filePath) throws IOException {
        return cleanText(Files.readString(filePath, StandardCharsets.UTF_8));
    }

    private static String parseTxt(InputStream inputStream) throws IOException {
        return cleanText(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new ParamException("filename must not be blank");
        }
        String cleanFilename = StringUtils.cleanPath(filename);
        int dotIndex = cleanFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleanFilename.length() - 1) {
            throw new ParamException("filename extension is required");
        }
        return cleanFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
