package com.example.insurancerag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RedactionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String ANALYZER_URL = "http://localhost:5002/analyze";
    private final String ANONYMIZER_URL = "http://localhost:5001/anonymize";

    // Returns a record of the raw analysis for compliance/auditing metadata
    public List<Map<String, Object>> analyzeText(String rawText) {
        try {
            Map<String, Object> request = Map.of(
                    "text", rawText,
                    "language", "en",
                    "entities", List.of("PERSON", "PHONE_NUMBER", "EMAIL_ADDRESS", "US_SSN", "LOCATION", "MEDICAL_RECORD")
            );
            
            String responseStr = restTemplate.postForObject(ANALYZER_URL, request, String.class);
            return objectMapper.readValue(responseStr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("PII Analyzer failed to respond", e);
        }
    }

    // Requests replacement maskings (e.g., replacement with "<PERSON>")
    public String anonymizeText(String rawText, List<Map<String, Object>> analyzerResults) {
        try {
            Map<String, Object> request = Map.of(
                    "text", rawText,
                    "analyzer_results", analyzerResults
            );
            
            Map<?, ?> response = restTemplate.postForObject(ANONYMIZER_URL, request, Map.class);
            return response != null ? (String) response.get("text") : rawText;
        } catch (Exception e) {
            throw new RuntimeException("PII Anonymizer failed to resolve masking mapping", e);
        }
    }

    // Logic evaluating risk thresholds to flag manual review
    public boolean requiresManualReview(List<Map<String, Object>> analysisResults) {
        for (Map<String, Object> entity : analysisResults) {
            String type = (String) entity.get("entity_type");
            double score = ((Number) entity.get("score")).doubleValue();

            // Strict compliance threshold: Any SSN, Medical ID, or high-confidence person match defaults to review
            if ("US_SSN".equals(type) || "MEDICAL_RECORD".equals(type) || ("PERSON".equals(type) && score > 0.85)) {
                return true;
            }
        }
        return false;
    }
}
