package com.erp.integrationhub.infrastructure.adapter.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * Uploads binary image assets to Cloudflare R2 (S3-compatible object storage).
 */
@Service
public class R2MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(R2MediaStorageService.class);

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final String publicUrl;
    private volatile S3Client s3Client;

    public R2MediaStorageService(
            @Value("${erp.integration.storage.endpoint:https://8d009c5a27b703c2906fd1b00b96908c.r2.cloudflarestorage.com}") String endpoint,
            @Value("${erp.integration.storage.bucket:driveos}") String bucket,
            @Value("${erp.integration.storage.access-key:}") String accessKey,
            @Value("${erp.integration.storage.secret-key:}") String secretKey,
            @Value("${erp.integration.storage.public-url:https://8d009c5a27b703c2906fd1b00b96908c.r2.cloudflarestorage.com/driveos}") String publicUrl) {
        this.endpoint = endpoint != null ? endpoint.trim().replaceAll("/+$", "") : "";
        this.bucket = bucket != null ? bucket.trim() : "driveos";
        this.accessKey = accessKey != null ? accessKey.trim() : "";
        this.secretKey = secretKey != null ? secretKey.trim() : "";
        this.publicUrl = publicUrl != null ? publicUrl.trim().replaceAll("/+$", "") : "";
    }

    public StoredMediaResult upload(MultipartFile file, String folder) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("Cloudflare R2 credentials (S3_ACCESS_KEY_ID / S3_SECRET_ACCESS_KEY) are not configured");
        }

        String sanitizedFolder = (folder != null && !folder.isBlank())
                ? folder.trim().replaceAll("[^a-zA-Z0-9/_-]", "").replaceAll("^/+|/+$", "")
                : "uploads";
        if (sanitizedFolder.isBlank()) {
            sanitizedFolder = "uploads";
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        String cleanName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        UUID mediaId = UUID.randomUUID();
        String objectKey = sanitizedFolder + "/" + mediaId.toString().substring(0, 8) + "-" + cleanName;

        try {
            byte[] bytes = file.getBytes();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength((long) bytes.length)
                    .build();

            getOrCreateClient().putObject(request, RequestBody.fromBytes(bytes));
            String url = publicUrl + "/" + objectKey;
            log.info("[R2 STORAGE] Uploaded mediaId={} key={} size={} bytes", mediaId, objectKey, bytes.length);
            return new StoredMediaResult(mediaId.toString(), url, objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file bytes", e);
        }
    }

    private S3Client getOrCreateClient() {
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    s3Client = S3Client.builder()
                            .endpointOverride(URI.create(endpoint))
                            .region(Region.of("auto"))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(accessKey, secretKey)))
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .build())
                            .httpClientBuilder(UrlConnectionHttpClient.builder())
                            .build();
                }
            }
        }
        return s3Client;
    }

    public record StoredMediaResult(String mediaId, String url, String objectKey) {}
}
