package com.example.insurancerag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RAGController {

    private final RetrievalQueryService queryService;

    public RAGController(RetrievalQueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, String>> askPolicy(
            @RequestParam String query,
            @RequestParam String state,
            @RequestParam String policyType,
            @RequestParam String year) {

        String answer = queryService.executeRAGQuery(query, state, policyType, year);

        return ResponseEntity.ok(Map.of(
                "query", query,
                "answer", answer
        ));
    }
}
