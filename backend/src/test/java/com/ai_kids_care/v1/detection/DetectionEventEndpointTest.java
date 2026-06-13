package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stop-bleed contract for detection event reads while authorization is absent.
 */
class DetectionEventEndpointTest extends BaseIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void listDetectionEvents_isNotPublished() {
        var resp = rest.getForEntity("/api/v1/detection_events?kindergartenId=1", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void detectionEventDetail_isNotPublished() {
        var resp = rest.getForEntity("/api/v1/detection_events/1?kindergartenId=1", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
