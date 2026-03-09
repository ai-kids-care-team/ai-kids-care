package com.dashboard.service;

import com.dashboard.dto.DashboardMetricDto;
import com.dashboard.entity.DashboardMetric;
import com.dashboard.repository.DashboardMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMetricRepository metricRepository;

    public List<DashboardMetricDto> getMetrics() {
        return metricRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DashboardMetricDto toDto(DashboardMetric metric) {
        return new DashboardMetricDto(
                metric.getId(),
                metric.getMetricName(),
                metric.getValue(),
                metric.getUnit(),
                metric.getCreatedAt()
        );
    }
}
