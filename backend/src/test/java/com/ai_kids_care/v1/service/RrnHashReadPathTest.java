package com.ai_kids_care.v1.service;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.security.RrnHashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0024 D3: ChildrenService.getChildEntityByRRN 의 이중 읽기 경로 통합 테스트.
 *
 * <ul>
 *   <li>경로 1 — HMAC 명중: 시드 row(child_id=1/2)는 rrn_hash 가 있으므로 직접 index scan 으로 명중.
 *   <li>경로 2 — BCrypt 회퇴 + 게으른 역충전: 픽스처로 rrn_hash=NULL + rrn_encrypted=BCrypt 행 삽입,
 *       조회 후 rrn_hash 가 채워졌는지 DB 에서 확인.
 *   <li>경로 3 — 없는 RRN 은 Optional.empty() 반환.
 * </ul>
 */
class RrnHashReadPathTest extends BaseIntegrationTest {

    @Autowired
    private ChildrenService childrenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${rrn.hash.pepper}")
    private String pepper;

    /**
     * 경로 1: HMAC 명중 — child_id=1 (박수아) REDACTED-RRN.
     * 시드에 rrn_hash 가 이미 있으므로 BCrypt 경로를 거치지 않는다.
     */
    @Test
    void hmacHitPath_child1_200921_4037926() {
        Optional<Child> result = childrenService.getChildEntityByRRN("200921", "4037926");
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    /**
     * 경로 1: HMAC 명중 — child_id=2 (강시윤) REDACTED-RRN.
     */
    @Test
    void hmacHitPath_child2_200319_3045123() {
        Optional<Child> result = childrenService.getChildEntityByRRN("200319", "3045123");
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(2L);
    }

    private static final String BACKFILL_CHILD_NO = "TESTHASH_BCRYPT_BACKFILL";
    private Long backfillChildId;

    @BeforeEach
    void insertBcryptOnlyFixture() {
        // Cleanup any leftover from a previous run (shared container)
        jdbc.update("DELETE FROM children WHERE child_no = ?", BACKFILL_CHILD_NO);

        // Insert a legacy BCrypt-only child row (rrn_hash IS NULL).
        // Use BCrypt cost=4 for test speed.
        String testFirst6 = "199912";
        String testBack7 = "9999991";
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        String bcryptOfBack7 = bcrypt.encode(testBack7);

        backfillChildId = jdbc.queryForObject(
                """
                INSERT INTO children (kindergarten_id, name, child_no, rrn_first6, rrn_encrypted, rrn_hash,
                                      birth_date, gender, address, enroll_date, status, created_at, updated_at)
                VALUES (1, 'RrnHashTest_Backfill', ?, ?, ?, NULL,
                        '2019-12-01', 'MALE', '테스트주소', '2023-03-01', 'DISABLED', NOW(), NOW())
                RETURNING child_id
                """,
                Long.class,
                BACKFILL_CHILD_NO, testFirst6, bcryptOfBack7
        );
    }

    @AfterEach
    void cleanupBcryptFixture() {
        jdbc.update("DELETE FROM children WHERE child_no = ?", BACKFILL_CHILD_NO);
    }

    /**
     * 경로 2: BCrypt 회퇴 + 게으른 역충전.
     *
     * <p>@BeforeEach 에서 삽입한 rrn_hash=NULL + rrn_encrypted=BCrypt("9999991") 행을 조회.
     * 조회 후 DB 에서 해당 행의 rrn_hash 가 채워졌는지 확인.
     * 트랜잭션 분리: 픽스처는 커밋됨, 서비스 메서드는 REQUIRES_NEW 로 독립 트랜잭션 사용.
     */
    @Test
    void bcryptFallbackLazyBackfill_writesRrnHash() {
        String testFirst6 = "199912";
        String testBack7 = "9999991";

        // Act
        Optional<Child> result = childrenService.getChildEntityByRRN(testFirst6, testBack7);

        // Assert: service found the child via BCrypt fallback
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(backfillChildId);

        // Assert: rrn_hash is now backfilled in DB
        String rrnHashInDb = jdbc.queryForObject(
                "SELECT rrn_hash FROM children WHERE child_id = ?",
                String.class, backfillChildId
        );
        String expectedHash = RrnHashUtil.hash(pepper, testFirst6, testBack7);
        assertThat(rrnHashInDb).isEqualTo(expectedHash);
    }

    /**
     * 경로 3: 없는 RRN — Optional.empty() 반환.
     */
    @Test
    void unknownRrn_returnsEmpty() {
        Optional<Child> result = childrenService.getChildEntityByRRN("000000", "0000000");
        assertThat(result).isEmpty();
    }
}
