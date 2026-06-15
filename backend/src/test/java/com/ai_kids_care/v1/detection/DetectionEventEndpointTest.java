package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Detection event reads are not published and not whitelisted, so default-deny
 * returns 401 to anonymous callers (path absence stays covered by the OpenAPI
 * contract test).
 */
class DetectionEventEndpointTest extends BaseIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void listDetectionEvents_isNotPublished() {
        var resp = rest.getForEntity("/api/v1/detection_events?kindergartenId=1", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void detectionEventDetail_isNotPublished() {
        var resp = rest.getForEntity("/api/v1/detection_events/1?kindergartenId=1", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
