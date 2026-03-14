package com.ecom.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class S3ImageService {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    // ── Upload image to S3 — returns public URL ──
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            return "default.jpg";
        }

        String fileName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            S3Client s3 = buildS3Client();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3.putObject(request, RequestBody.fromBytes(file.getBytes()));
            String imageUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + fileName;
            log.info("Image uploaded to S3: {}", imageUrl);
            return imageUrl;

        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            // Fallback — save locally if S3 fails
            return file.getOriginalFilename();
        }
    }

    private S3Client buildS3Client() {
        if (accessKey != null && !accessKey.isEmpty()) {
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        // Use IAM role if no keys provided (best practice on EC2/EKS)
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
