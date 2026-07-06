package com.ai_kids_care.v1.security;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UX-07 wire-password-management, backend lane task 2.1: direct coverage of the Redis-backed
 * challenge/token store (real Redis via {@link BaseIntegrationTest}'s testcontainer) — the
 * per-HTTP-endpoint tests in {@code PasswordResetFlowTest} cover the same invariants end-to-end,
 * this class isolates the store's own state machine (TTL, attempts, single-use token).
 */
class PasswordResetTokenServiceTest extends BaseIntegrationTest {

    @Autowired private PasswordResetTokenService service;

    @Test
    void verify_correctCodeForRealChallenge_issuesResetTokenAndConsumesChallenge() {
        PasswordResetTokenService.Challenge challenge = service.createChallenge(42L);

        PasswordResetTokenService.VerifyResult result = service.verify(challenge.challengeId(), challenge.code());

        assertThat(result.success()).isTrue();
        assertThat(result.resetToken()).isNotBlank();
        assertThat(result.expiresAt()).isNotNull();

        // Challenge is single-use: a second verify (even with the right code) now fails.
        PasswordResetTokenService.VerifyResult replay = service.verify(challenge.challengeId(), challenge.code());
        assertThat(replay.success()).isFalse();
    }

    @Test
    void verify_dummyChallengeWithNullUserId_neverSucceedsEvenWithCorrectCode() {
        PasswordResetTokenService.Challenge dummy = service.createChallenge(null);

        PasswordResetTokenService.VerifyResult result = service.verify(dummy.challengeId(), dummy.code());

        assertThat(result.success()).isFalse();
        assertThat(result.resetToken()).isNull();
    }

    @Test
    void verify_wrongCode_fails() {
        PasswordResetTokenService.Challenge challenge = service.createChallenge(7L);

        PasswordResetTokenService.VerifyResult result = service.verify(challenge.challengeId(), "000000".equals(challenge.code()) ? "111111" : "000000");

        assertThat(result.success()).isFalse();
    }

    @Test
    void verify_unknownChallengeId_fails() {
        PasswordResetTokenService.VerifyResult result = service.verify(
                java.util.UUID.randomUUID().toString(), "123456");

        assertThat(result.success()).isFalse();
    }

    @Test
    void verify_attemptsExceedCap_locksChallenge() {
        PasswordResetTokenService.Challenge challenge = service.createChallenge(9L);
        String wrongCode = "000000".equals(challenge.code()) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(service.verify(challenge.challengeId(), wrongCode).success()).isFalse();
        }

        // Locked out even with the correct code now.
        PasswordResetTokenService.VerifyResult result = service.verify(challenge.challengeId(), challenge.code());
        assertThat(result.success()).isFalse();
    }

    @Test
    void consumeResetToken_singleUse_secondConsumeReturnsNull() {
        PasswordResetTokenService.Challenge challenge = service.createChallenge(55L);
        PasswordResetTokenService.VerifyResult verified = service.verify(challenge.challengeId(), challenge.code());
        assertThat(verified.success()).isTrue();

        Long userId = service.consumeResetToken(verified.resetToken());
        assertThat(userId).isEqualTo(55L);

        Long secondConsume = service.consumeResetToken(verified.resetToken());
        assertThat(secondConsume).isNull();
    }

    @Test
    void consumeResetToken_unknownToken_returnsNull() {
        assertThat(service.consumeResetToken("does-not-exist")).isNull();
    }
}
