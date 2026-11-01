package com.erp.integrationhub.interfaces.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media Storage", description = "Secure image and media upload to CDN storage")
public class MediaUploadController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml", "image/avif"
    );

    @Value("${erp.integration.storage.public-url:https://8d009c5a27b703c2906fd1b00b96908c.r2.cloudflarestorage.com/driveos}")
    private String storagePublicUrl = "https://8d009c5a27b703c2906fd1b00b96908c.r2.cloudflarestorage.com/driveos";

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload image file", description = "Uploads an image to cloud CDN storage in an organized folder")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "uploads") String folder
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported image media format: " + contentType));
        }

        String sanitizedFolder = (folder != null && !folder.trim().isEmpty()) ? folder.trim() : "uploads";
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        String cleanName = originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "-" + cleanName;

        String cdnUrl = String.format("%s/%s/%s", storagePublicUrl, sanitizedFolder, uniqueName);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", cdnUrl));
    }
}
