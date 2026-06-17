package com.ai_kids_care.v1.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RrnHashUtil 단위 테스트 — ADR-0024 D1.
 *
 * <p>JDK 표준 라이브러리만 사용하므로 Spring 컨텍스트 불필요.
 * 알려진 벡터는 독립 Python 스크립트로 선(先) 산출 후 hardcode 했다.
 */
class RrnHashUtilTest {

    private static final String TEST_PEPPER = "test-pepper-not-secret-2026";

    /**
     * 알려진 벡터 1 — child_id=1 (박수아) REDACTED-RRN.
     * 기댓값은 Python/hmac.new(key, msg, sha256) + base64.urlsafe_b64encode.rstrip('=') 로 산출.
     */
    @Test
    void knownVector_child1_박수아() {
        String result = RrnHashUtil.hash(TEST_PEPPER, "200921", "4037926");
        assertThat(result).isEqualTo("0Vtg2m20v_7y5MW6tlmYIl2e51mKmM4C8OwmCTUz0x0");
    }

    /**
     * 알려진 벡터 2 — child_id=2 (강시윤) REDACTED-RRN.
     */
    @Test
    void knownVector_child2_강시윤() {
        String result = RrnHashUtil.hash(TEST_PEPPER, "200319", "3045123");
        assertThat(result).isEqualTo("2QFnVjZ-x60enHVQjzk4zQAuYNQQVtMGWA6X9mTjEmw");
    }

    /**
     * 같은 입력은 같은 출력을 반환한다(멱등성).
     */
    @Test
    void idempotency_sameInputProducesSameOutput() {
        String first = RrnHashUtil.hash(TEST_PEPPER, "200921", "4037926");
        String second = RrnHashUtil.hash(TEST_PEPPER, "200921", "4037926");
        assertThat(first).isEqualTo(second);
    }

    /**
     * 연결 순서 검증: first6 ‖ back7 이어야 하며 back7 ‖ first6 와 달라야 한다.
     */
    @Test
    void concatenationOrder_first6ThenBack7() {
        String correctOrder = RrnHashUtil.hash(TEST_PEPPER, "200921", "4037926");
        // Reversed: back7 ‖ first6 — should differ
        String reversedOrder = RrnHashUtil.hash(TEST_PEPPER, "4037926", "200921");
        assertThat(correctOrder).isNotEqualTo(reversedOrder);
        // Confirm correctOrder matches known vector (first6 ‖ back7)
        assertThat(correctOrder).isEqualTo("0Vtg2m20v_7y5MW6tlmYIl2e51mKmM4C8OwmCTUz0x0");
    }

    /**
     * pepper 가 빈 문자열이면 IllegalArgumentException 을 던진다.
     */
    @Test
    void emptyPepper_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> RrnHashUtil.hash("", "200921", "4037926"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pepper");
    }

    /**
     * pepper 가 null 이면 IllegalArgumentException 을 던진다.
     */
    @Test
    void nullPepper_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> RrnHashUtil.hash(null, "200921", "4037926"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pepper");
    }

    /**
     * pepper 가 공백만 있으면 IllegalArgumentException 을 던진다.
     */
    @Test
    void blankPepper_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> RrnHashUtil.hash("   ", "200921", "4037926"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pepper");
    }
}
