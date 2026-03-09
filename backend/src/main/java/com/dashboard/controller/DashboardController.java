package com.dashboard.controller;

import com.dashboard.dto.DashboardMetricDto;
import com.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    public ResponseEntity<List<DashboardMetricDto>> getMetrics() {
        List<DashboardMetricDto> metrics = dashboardService.getMetrics();
        return ResponseEntity.ok(metrics);
    }
}
