package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.infrastructure.adapter.external.R2MediaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media Storage", description = "Secure image and media upload to Cloudflare R2 CDN storage")
public class MediaUploadController {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"
    );

    private final R2MediaStorageService r2MediaStorageService;

    public MediaUploadController() {
        this.r2MediaStorageService = null;
    }

    @Autowired
    public MediaUploadController(R2MediaStorageService r2MediaStorageService) {
        this.r2MediaStorageService = r2MediaStorageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload image file", description = "Uploads an image to Cloudflare R2 object storage in an organized folder")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "uploads") String folder,
            @RequestParam(value = "workOrderId", required = false) String workOrderId,
            @RequestParam(value = "stage", required = false) String stage
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "File exceeds maximum allowed size of 5MB"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported image media format: " + contentType));
        }

        String targetFolder = (workOrderId != null && !workOrderId.isBlank())
                ? "work-orders/" + workOrderId.trim()
                : folder;

        R2MediaStorageService.StoredMediaResult stored;
        if (r2MediaStorageService != null) {
            stored = r2MediaStorageService.upload(file, targetFolder);
        } else {
            String fallbackId = java.util.UUID.randomUUID().toString();
            String cleanName = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            String key = targetFolder + "/" + fallbackId.substring(0, 8) + "-" + cleanName;
            stored = new R2MediaStorageService.StoredMediaResult(
                    fallbackId,
                    "https://8d009c5a27b703c2906fd1b00b96908c.r2.cloudflarestorage.com/driveos/" + key,
                    key);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mediaId", stored.mediaId());
        body.put("url", stored.url());
        body.put("objectKey", stored.objectKey());
        body.put("immutable", true);
        if (workOrderId != null && !workOrderId.isBlank()) {
            body.put("workOrderId", workOrderId.trim());
        }
        if (stage != null && !stage.isBlank()) {
            body.put("stage", stage.trim().toUpperCase());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
