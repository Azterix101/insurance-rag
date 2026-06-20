package com.example.insurancerag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/indexing")
public class IndexingController {

    private final PolicyIndexingService indexingService;

    public IndexingController(PolicyIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @PostMapping("/policy")
    public ResponseEntity<Map<String, String>> indexPolicy(
            @RequestParam String state,
            @RequestParam String policyType,
            @RequestParam String year,
            @RequestBody String markdownPayload) {

        indexingService.parseAndIndexPolicy(markdownPayload, state, policyType, year);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Policy successfully parsed, split into Parent/Child nodes, vectorized, and committed to pgvector."
        ));
    }
}
