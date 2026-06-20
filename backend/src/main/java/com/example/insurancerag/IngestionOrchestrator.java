package com.example.insurancerag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class IngestionOrchestrator {

    private final S3Client s3Client;
    private final RedactionService redactionService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestionOrchestrator(S3Client s3Client, RedactionService redactionService, JdbcTemplate jdbcTemplate) {
        this.s3Client = s3Client;
        this.redactionService = redactionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String processDocument(String filename) {
        try {
            // 1. Download file from our local S3 "raw-claims" bucket
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket("raw-claims")
                    .key(filename)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getRequest);
            String rawText = objectBytes.asString(StandardCharsets.UTF_8);

            // 2. Perform automated PII identification
            List<Map<String, Object>> piiAnalysis = redactionService.analyzeText(rawText);
            String anonymizedText = redactionService.anonymizeText(rawText, piiAnalysis);

            // 3. Human-in-the-Loop decision routing
            if (redactionService.requiresManualReview(piiAnalysis)) {
                String piiJson = objectMapper.writeValueAsString(piiAnalysis);
                
                // Route to human review table
                String ddl = """
                    INSERT INTO compliance_queue (document_name, raw_s3_uri, redacted_content, flagged_entities, status)
                    VALUES (?, ?, ?, ?, 'PENDING_REVIEW')
                """;
                jdbcTemplate.update(ddl, filename, "s3://raw-claims/" + filename, anonymizedText, piiJson);
                
                return "FLAGGED_FOR_REVIEW: High-risk sensitive entities were detected. Document has been locked and routed to the compliance queue.";
            } else {
                // Safe document: Upload immediately to sanitized storage
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket("sanitized-claims")
                        .key(filename)
                        .contentType("text/plain")
                        .build();

                s3Client.putObject(putRequest, RequestBody.fromString(anonymizedText));
                return "SUCCESS: Document sanitized and promoted directly to sanitized-claims S3 storage.";
            }
        } catch (Exception e) {
            throw new RuntimeException("Orchestration pipeline encountered an error: " + e.getMessage(), e);
        }
    }
}
