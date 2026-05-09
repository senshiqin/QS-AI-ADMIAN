package com.qs.ai.admian.service;

import com.qs.ai.admian.controller.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * File upload service.
 */
public interface FileUploadService {

    FileUploadResponse uploadSingle(MultipartFile file);

    List<FileUploadResponse> uploadMultiple(List<MultipartFile> files);
}
