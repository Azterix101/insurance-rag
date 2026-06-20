package com.example.insurancerag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final IngestionOrchestrator orchestrator;

    public IngestionController(IngestionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> triggerIngestion(@RequestParam String filename) {
        String result = orchestrator.processDocument(filename);
        return ResponseEntity.ok(Map.of(
                "filename", filename,
                "status", result
        ));
    }
}

