package com.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_metrics")
@Getter
@Setter
@NoArgsConstructor
public class DashboardMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private String unit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DashboardMetric(String metricName, Double value, String unit) {
        this.metricName = metricName;
        this.value = value;
        this.unit = unit;
        this.createdAt = LocalDateTime.now();
    }
}
