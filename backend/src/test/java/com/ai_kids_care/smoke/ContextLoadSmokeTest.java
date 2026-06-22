package com.ai_kids_care.smoke;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toolchain + foundation smoke test.
 *
 * Proves the whole integration-test stack boots under the `test` profile:
 *   - Testcontainers PostgreSQL starts and is seeded from db/initdb
 *   - Flyway baseline (V1) + V2–V6 apply against that schema
 *   - JPA ddl-auto=validate passes (no entity/schema drift)
 *   - Redis Testcontainer wires Spring Session
 *   - The full Spring application context starts
 *
 * If this is green, the per-capability behaviour tests can build on the same base.
 */
class ContextLoadSmokeTest extends BaseIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void applicationContextStartsUnderTestProfile() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
    }
}
