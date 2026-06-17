package com.ai_kids_care.v1.service;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.entity.Child;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0024 D4 (Phase 3): ChildrenService.getChildEntityByRRN HMAC 단일 읽기 경로 통합 테스트.
 *
 * <ul>
 *   <li>경로 1 — HMAC 명중: 시드 row(child_id=1/2)는 rrn_hash 가 있으므로 직접 index scan 으로 명중.
 *   <li>경로 2 — 없는 RRN 은 Optional.empty() 반환.
 * </ul>
 *
 * BCrypt 회퇴 + 게으른 역충전 경로는 V5/V6 적용으로 제거되었다.
 */
class RrnHashReadPathTest extends BaseIntegrationTest {

    @Autowired
    private ChildrenService childrenService;

    /**
     * 경로 1: HMAC 명중 — child_id=1 (박수아) REDACTED-RRN.
     * 시드에 rrn_hash 가 이미 있으므로 직접 index scan 으로 명중.
     */
    @Test
    void hmacHitPath_child1_200921_4037926() {
        Optional<Child> result = childrenService.getChildEntityByRRN("200921", "4037926");
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getRrnHash()).isNotNull();
    }

    /**
     * 경로 1: HMAC 명중 — child_id=2 (강시윤) REDACTED-RRN.
     */
    @Test
    void hmacHitPath_child2_200319_3045123() {
        Optional<Child> result = childrenService.getChildEntityByRRN("200319", "3045123");
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(2L);
        assertThat(result.get().getRrnHash()).isNotNull();
    }

    /**
     * 경로 2: 없는 RRN — Optional.empty() 반환.
     */
    @Test
    void unknownRrn_returnsEmpty() {
        Optional<Child> result = childrenService.getChildEntityByRRN("000000", "0000000");
        assertThat(result).isEmpty();
    }
}
