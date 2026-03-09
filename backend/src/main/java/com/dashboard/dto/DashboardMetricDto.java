package com.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricDto {

    private Long id;
    private String metricName;
    private Double value;
    private String unit;
    private LocalDateTime createdAt;
}
