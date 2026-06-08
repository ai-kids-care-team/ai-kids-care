package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for GET /api/v1/detection_events.
 *
 * Seed data from db/initdb/42_detection_events_seed.sql ensures the list is
 * non-empty.  Tests record current response shape (Spring Page<T> serialisation).
 */
class DetectionEventEndpointTest extends BaseIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void listDetectionEvents_returns200WithPageStructure() {
        var resp = rest.getForEntity("/api/v1/detection_events", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Spring Page<T> always contains these top-level keys
        assertThat(resp.getBody()).containsKeys("content", "totalElements", "totalPages", "size");
    }

    @Test
    void listDetectionEvents_seedDataPresent_contentIsNonEmpty() {
        var resp = rest.getForEntity("/api/v1/detection_events", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) resp.getBody().get("content");
        assertThat(content).isNotNull().isNotEmpty();
    }

    @Test
    void listDetectionEvents_withPageSize_respectsRequestedSize() {
        var resp = rest.getForEntity("/api/v1/detection_events?page=0&size=3", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) resp.getBody().get("content");
        assertThat(content).hasSizeLessThanOrEqualTo(3);
    }
}
