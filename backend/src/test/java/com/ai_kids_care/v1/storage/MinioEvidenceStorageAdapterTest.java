package com.ai_kids_care.v1.storage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link MinioEvidenceStorageAdapter}'s package-private static helpers — no
 * Spring context, no MinIO/testcontainer needed. Covers the storage_uri resolution rules
 * (design.md §B: s3:// -> key, bare key -> as-is, file:// -> unavailable) and the single-range
 * {@code Range} header parser backing the content endpoint's 206 support.
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
    void parseRange_startAtOrBeyondTotalSize_returnsNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange("bytes=1000-1005", 1000)).isNull();
    }

    @Test
    void parseRange_endBeforeStart_returnsNull() {
        assertThat(MinioEvidenceStorageAdapter.parseRange("bytes=500-100", 1000)).isNull();
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
}
