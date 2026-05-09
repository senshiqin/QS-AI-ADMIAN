package com.qs.ai.admian.service.impl;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import com.qs.ai.admian.exception.FileUploadException;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Local disk implementation for file uploads.
 */
@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${file.upload.base-dir:uploads/files}")
    private String uploadBaseDir;

    @Override
    public FileUploadResponse uploadSingle(MultipartFile file) {
        return storeFile(file);
    }

    @Override
    public List<FileUploadResponse> uploadMultiple(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ParamException("files must not be empty");
        }
        return files.stream()
                .map(this::storeFile)
                .toList();
    }

    private FileUploadResponse storeFile(MultipartFile file) {
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path targetDirectory = buildTargetDirectory();
        Path targetPath = targetDirectory.resolve(storedFilename).normalize();

        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(targetPath);
        } catch (IOException | IllegalStateException ex) {
            log.error("Failed to store uploaded file, originalFilename={}, targetPath={}",
                    originalFilename, targetPath, ex);
            throw new FileUploadException("Failed to store uploaded file");
        }

        return new FileUploadResponse(
                originalFilename,
                storedFilename,
                extension,
                file.getSize(),
                targetPath.toString()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null) {
            throw new ParamException("file must not be null");
        }
        if (file.isEmpty()) {
            throw new ParamException("file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ParamException("file size must be less than or equal to 100MB");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        if (!StringUtils.hasText(originalFilename)) {
            throw new ParamException("original filename must not be blank");
        }
        if (originalFilename.contains("..")) {
            throw new ParamException("original filename contains invalid path sequence");
        }

        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ParamException("file type must be one of: pdf, docx, txt, md");
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new ParamException("file extension must be one of: pdf, docx, txt, md");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Path buildTargetDirectory() {
        return Paths.get(uploadBaseDir)
                .resolve(LocalDate.now().format(DATE_PATH_FORMATTER))
                .toAbsolutePath()
                .normalize();
    }
}
