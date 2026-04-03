package com.github.report.controller;

import com.github.report.model.AccessReport;
import com.github.report.service.AccessReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;

@Slf4j
@RestController

@RequestMapping("/api")
public class AccessReportController {

    private final AccessReportService accessReportService;

    public AccessReportController(AccessReportService accessReportService) {
        this.accessReportService = accessReportService;
    }

    // ── GET /api/access-report?org=orgName ─────────────────────────────────
    @GetMapping("/access-report")
    public ResponseEntity<AccessReport> getAccessReport(
            @RequestParam @NotBlank(message = "Organization name cannot be blank") String org
    ) {
        log.info("Received request for access report — org: {}", org);
        AccessReport report = accessReportService.generateReport(org);
        return ResponseEntity.ok(report);
    }

    // ── GET /api/health — quick check that app is running ──────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("GitHub Access Report Service is running");
    }
}