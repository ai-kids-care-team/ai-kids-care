package com.ai_kids_care.v1.service;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QLT-04b (cleanup-landmines-and-masking C4): {@code AiModelService#listAiModels} keyword search
 * was implemented (mirrors {@code DetectionEventService#listDetectionEvents}) but had zero test
 * coverage. Mirrors {@code DetectionEventKeywordSearchTest} minus the tenant dimension —
 * {@code ai_models} carries no {@code kindergarten_id} (platform-scoped, {@code
 * PLATFORM_METADATA_READ}), so there is no cross-tenant leak scenario to assert here; instead this
 * covers case-insensitive keyword matching, blank-keyword no-op, and pagination pass-through.
 */
@AutoConfigureMockMvc
class AiModelKeywordSearchTest extends BaseIntegrationTest {

    private static final String PW = "Test@AiModelKw2026";
    private static final String PLATFORM_ADMIN_LOGIN = "aimodel-kw-platform-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private Long targetModelId;
    private Long noiseModelId;
    private final List<Long> pagingModelIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        upsertPlatformItAdmin();
        targetModelId = insertModel("Kw-VideoMAE-Assault-Target");
        noiseModelId = insertModel("Kw-Other-Noise-Model");
    }

    @AfterEach
    void tearDown() {
        deleteModel(targetModelId);
        deleteModel(noiseModelId);
        pagingModelIds.forEach(this::deleteModel);
        pagingModelIds.clear();
    }

    @Test
    void keyword_filtersCaseInsensitive() throws Exception {
        Cookie session = login(PLATFORM_ADMIN_LOGIN);

        // mixed-case keyword against the DB value "Kw-VideoMAE-Assault-Target" proves case-insensitivity.
        MvcResult result = mockMvc.perform(get("/api/v1/ai_models")
                        .param("keyword", "videoMAE-assault").param("size", "1000").cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        List<Long> ids = extractModelIds(result);
        assertThat(ids).contains(targetModelId);
        assertThat(ids).doesNotContain(noiseModelId);
    }

    @Test
    void blankKeyword_returnsFullUnfilteredList() throws Exception {
        Cookie session = login(PLATFORM_ADMIN_LOGIN);

        MvcResult withoutKeyword = mockMvc.perform(get("/api/v1/ai_models")
                        .param("size", "1000").cookie(session))
                .andExpect(status().isOk()).andReturn();
        MvcResult withBlankKeyword = mockMvc.perform(get("/api/v1/ai_models")
                        .param("keyword", "   ").param("size", "1000").cookie(session))
                .andExpect(status().isOk()).andReturn();

        List<Long> idsNoParam = extractModelIds(withoutKeyword);
        List<Long> idsBlank = extractModelIds(withBlankKeyword);

        assertThat(idsNoParam).contains(targetModelId, noiseModelId);
        assertThat(idsBlank).containsExactlyInAnyOrderElementsOf(idsNoParam);
    }

    @Test
    void pagination_isPassedThrough() throws Exception {
        // Three additional rows sharing a keyword, to assert page/size/totalElements pass-through
        // independent of the two fixture rows above.
        pagingModelIds.add(insertModel("Kw-Page-Item-Alpha"));
        pagingModelIds.add(insertModel("Kw-Page-Item-Bravo"));
        pagingModelIds.add(insertModel("Kw-Page-Item-Charlie"));

        Cookie session = login(PLATFORM_ADMIN_LOGIN);

        MvcResult firstPage = mockMvc.perform(get("/api/v1/ai_models")
                        .param("keyword", "kw-page-item").param("size", "2").param("page", "0")
                        .cookie(session))
                .andExpect(status().isOk()).andReturn();
        MvcResult secondPage = mockMvc.perform(get("/api/v1/ai_models")
                        .param("keyword", "kw-page-item").param("size", "2").param("page", "1")
                        .cookie(session))
                .andExpect(status().isOk()).andReturn();

        var firstTree = objectMapper.readTree(firstPage.getResponse().getContentAsString());
        var secondTree = objectMapper.readTree(secondPage.getResponse().getContentAsString());

        assertThat(firstTree.get("totalElements").asInt()).isEqualTo(3);
        assertThat(firstTree.get("content")).hasSize(2);
        assertThat(secondTree.get("totalElements").asInt()).isEqualTo(3);
        assertThat(secondTree.get("content")).hasSize(1);

        List<Long> firstIds = extractModelIds(firstPage);
        List<Long> secondIds = extractModelIds(secondPage);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        List<Long> combined = new java.util.ArrayList<>(firstIds);
        combined.addAll(secondIds);
        assertThat(combined).containsExactlyInAnyOrderElementsOf(pagingModelIds);
    }

    // ── response parsing ─────────────────────────────────────────────────────────

    private List<Long> extractModelIds(MvcResult result) throws Exception {
        var tree = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = new java.util.ArrayList<>();
        for (var node : tree.get("content")) {
            ids.add(node.get("modelId").asLong());
        }
        return ids;
    }

    // ── fixture helpers ───────────────────────────────────────────────────────────

    private Long insertModel(String name) {
        return jdbc.queryForObject("""
                INSERT INTO ai_models (name, version, status, created_at, updated_at)
                VALUES (?, '1.0.0', 'ACTIVE', NOW(), NOW())
                RETURNING model_id
                """, Long.class, name);
    }

    private void deleteModel(Long id) {
        if (id == null) {
            return;
        }
        jdbc.update("DELETE FROM ai_models WHERE model_id = ?", id);
    }

    private void upsertPlatformItAdmin() {
        String hash = passwordEncoder.encode(PW);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, PLATFORM_ADMIN_LOGIN, PLATFORM_ADMIN_LOGIN + "@test.local", "010-0000-9001", hash);
        jdbc.update("""
                DELETE FROM user_kindergarten_memberships
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, PLATFORM_ADMIN_LOGIN);
        jdbc.update("""
                DELETE FROM user_role_assignments
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, PLATFORM_ADMIN_LOGIN);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'PLATFORM_IT_ADMIN', 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, PLATFORM_ADMIN_LOGIN);
    }

    private Cookie login(String loginId) throws Exception {
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", loginId, "password", PW))))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        return request.cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }
}
