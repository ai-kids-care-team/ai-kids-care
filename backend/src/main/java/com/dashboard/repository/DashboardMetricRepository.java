package com.dashboard.repository;

import com.dashboard.entity.DashboardMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardMetricRepository extends JpaRepository<DashboardMetric, Long> {

    List<DashboardMetric> findAllByOrderByCreatedAtDesc();
}
