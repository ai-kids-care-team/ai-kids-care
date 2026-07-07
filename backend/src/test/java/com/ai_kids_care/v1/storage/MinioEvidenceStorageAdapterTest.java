package com.ai_kids_care.v1.storage;

import com.ai_kids_care.v1.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link MinioEvidenceStorageAdapter}'s package-private static helpers — no
 * Spring context, no MinIO/testcontainer needed. Covers the storage_uri resolution rules
 * (design.md §B: s3:// -> key, bare key -> as-is, file:// -> unavailable), the single-range
 * {@code Range} header parser backing the content endpoint's 206/416 support
 * (refine-evidence-readback-robustness), and the {@link EvidenceStoragePort#exists} real-presence
 * check (mocked {@link MinioClient}, no testcontainer needed for these either).
 */
class MinioEvidenceStorageAdapterTest {

    // ── resolveKey ────────────────────────────────────────────────────────────────

    @Test
    void resolveKey_s3Uri_returnsKeyIgnoringUriBucketSegment() {
        Optional<String> key = MinioEvidenceStorageAdapter.resolveKey("s3://ai-kids-care/evidence/seed-event-001.jpg");
        assertThat(key).contains("evidence/seed-event-001.jpg");
    }

    @Test
    void resolveKey_s3UriWithNoKeySegment_returnsEmpty() {
        assertThat(MinioEvidenceStorageAdapter.resolveKey("s3://ai-kids-care")).isEmpty();
        assertThat(MinioEvidenceStorageAdapter.resolveKey("s3://ai-kids-care/")).isEmpty();
    }

    @Test
    void resolveKey_bareKey_returnsAsIs() {
        assertThat(MinioEvidenceStorageAdapter.resolveKey("evidence/1/2/3.mp4"))
                .contains("evidence/1/2/3.mp4");
    }

    @Test
    void resolveKey_fileUri_isUnavailable() {
        assertThat(MinioEvidenceStorageAdapter.resolveKey("file:///var/data/legacy-clip.mp4")).isEmpty();
    }

    @Test
    void resolveKey_unrecognizedScheme_isUnavailable() {
        assertThat(MinioEvidenceStorageAdapter.resolveKey("http://example.com/evidence.jpg")).isEmpty();
    }

    @Test
    void resolveKey_nullOrBlank_isEmpty() {
        assertThat(MinioEvidenceStorageAdapter.resolveKey(null)).isEmpty();
        assertThat(MinioEvidenceStorageAdapter.resolveKey("")).isEmpty();
        assertThat(MinioEvidenceStorageAdapter.resolveKey("   ")).isEmpty();
    }

    // ── parseRange ───────────────────────────────────────────────────────────────

    @Test
    void parseRange_absentHeader_returnsNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange(null, 1000)).isNull();
    }

    @Test
    void parseRange_startEnd_withinBounds_parses() {
        long[] range = MinioEvidenceStorageAdapter.parseRange("bytes=200-499", 1000);
        assertThat(range).containsExactly(200L, 499L);
    }

    @Test
    void parseRange_openEnded_resolvesToTotalSizeMinusOne() {
        long[] range = MinioEvidenceStorageAdapter.parseRange("bytes=900-", 1000);
        assertThat(range).containsExactly(900L, 999L);
    }

    @Test
    void parseRange_endBeyondTotalSize_isClampedToLastByte() {
        long[] range = MinioEvidenceStorageAdapter.parseRange("bytes=0-999999", 1000);
        assertThat(range).containsExactly(0L, 999L);
    }

    @Test
    void parseRange_startAtOrBeyondTotalSize_throwsUnsatisfiableRange() {
        // refine-evidence-readback-robustness: a well-formed single-range header whose start is at or
        // beyond the object's end is a 416 case (RFC 7233), not a silent 200 fallback.
        assertThatThrownBy(() -> MinioEvidenceStorageAdapter.parseRange("bytes=1000-1005", 1000))
                .isInstanceOf(UnsatisfiableRangeException.class)
                .satisfies(e -> assertThat(((UnsatisfiableRangeException) e).getTotalSize()).isEqualTo(1000L));
    }

    @Test
    void parseRange_endBeforeStart_throwsUnsatisfiableRange() {
        assertThatThrownBy(() -> MinioEvidenceStorageAdapter.parseRange("bytes=500-100", 1000))
                .isInstanceOf(UnsatisfiableRangeException.class)
                .satisfies(e -> assertThat(((UnsatisfiableRangeException) e).getTotalSize()).isEqualTo(1000L));
    }

    @Test
    void parseRange_multiRange_isUnsupportedFallsBackToNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange("bytes=0-10,20-30", 1000)).isNull();
    }

    @Test
    void parseRange_suffixRange_isUnsupportedFallsBackToNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange("bytes=-500", 1000)).isNull();
    }

    @Test
    void parseRange_malformed_returnsNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange("not-a-range", 1000)).isNull();
    }

    @Test
    void parseRange_zeroOrNegativeTotalSize_returnsNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange("bytes=0-10", 0)).isNull();
    }

    // ── exists ───────────────────────────────────────────────────────────────────

    private MinioClient minioClient;
    private MinioEvidenceStorageAdapter adapter;

    @BeforeEach
    void setUpExists() {
        minioClient = mock(MinioClient.class);
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setBucket("ai-kids-care");
        adapter = new MinioEvidenceStorageAdapter(minioClient, properties);
    }

    @Test
    void exists_fileUri_returnsFalseWithoutIssuingAnyStoreRequest() throws Exception {
        boolean available = adapter.exists("file:///var/data/legacy-clip.mp4");

        assertThat(available).isFalse();
        verify(minioClient, never()).statObject(any());
    }

    @Test
    void exists_objectPresent_returnsTrue() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));

        boolean available = adapter.exists("s3://ai-kids-care/evidence/present.jpg");

        assertThat(available).isTrue();
    }

    @Test
    void exists_noSuchKey_returnsFalse() throws Exception {
        // NOTE: the exception must be fully built BEFORE the outer when(...).thenThrow(...) call —
        // passing a method that itself calls when()/thenReturn() as thenThrow()'s argument corrupts
        // Mockito's ongoing-stubbing state (argument evaluation runs before the outer stub attaches).
        ErrorResponseException noSuchKey = noSuchKeyError();
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(noSuchKey);

        boolean available = adapter.exists("s3://ai-kids-care/evidence/missing.jpg");

        assertThat(available).isFalse();
    }

    @Test
    void exists_nonNoSuchKeyStoreFailure_isNotSwallowedIntoFalse() throws Exception {
        // A reachability/auth/IO failure must never be reported the same as "the evidence is gone" —
        // it has to propagate so the caller (and its 500) reflects "the store is broken", not a
        // false-negative "no evidence".
        ErrorResponseException accessDenied = accessDeniedError();
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(accessDenied);

        assertThatThrownBy(() -> adapter.exists("s3://ai-kids-care/evidence/whatever.jpg"))
                .isInstanceOf(EvidenceStorageException.class);
    }

    private static ErrorResponseException noSuchKeyError() {
        ErrorResponse body = mock(ErrorResponse.class);
        when(body.code()).thenReturn("NoSuchKey");
        ErrorResponseException ex = mock(ErrorResponseException.class);
        when(ex.errorResponse()).thenReturn(body);
        return ex;
    }

    private static ErrorResponseException accessDeniedError() {
        ErrorResponse body = mock(ErrorResponse.class);
        when(body.code()).thenReturn("AccessDenied");
        ErrorResponseException ex = mock(ErrorResponseException.class);
        when(ex.errorResponse()).thenReturn(body);
        return ex;
    }
}
