package com.portfolio.eventbooking.controller;

import com.portfolio.eventbooking.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the in-memory counters from MetricsService as a plain JSON
 * snapshot. Per the brief, "even just logged counters is fine" — this
 * endpoint exists in ADDITION to the periodic log line (see
 * MetricsLogger), since for a portfolio project being able to hit one URL
 * and see current numbers is a nicer interview demo than asking someone
 * to tail logs.
 */
@RestController
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/api/metrics")
    public ResponseEntity<MetricsService.Snapshot> getMetrics() {
        return ResponseEntity.ok(metricsService.snapshot());
    }
}
