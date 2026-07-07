package com.ai_kids_care.v1.storage;

/**
 * D-STORE outbound port for reading evidence bytes out of object storage (MinIO in production).
 * Kept as a narrow interface (not a repository, not a service) so the MinIO SDK never leaks past
 * {@link MinioEvidenceStorageAdapter} — see design.md §"组件落点".
 */
public interface EvidenceStoragePort {

    /**
     * Cheap, no-network availability check used by the evidence CONTENT endpoint's pre-flight guard
     * (see {@code EventEvidenceFileService#openContentStream}). Resolves the {@code storage_uri}
     * scheme only: legacy {@code file://} rows (written before MinIO was wired in) are always
     * unavailable; {@code s3://} and bare-key rows are optimistically available (a since-deleted
     * object still 404s from {@link #open}'s own {@code statObject}, just not surfaced here). Kept
     * separate from {@link #exists} so the content path never pays for two {@code statObject} round
     * trips (this scheme-only check, plus {@code open}'s own size lookup).
     */
    boolean isAvailable(String storageUri);

    /**
     * Real existence check used by the evidence LIST endpoint (refine-evidence-readback-robustness):
     * confirms the object is actually present in the store, not just that its {@code storage_uri}
     * scheme is theoretically readable. For a non-resolvable URI (e.g. legacy {@code file://}) this
     * returns {@code false} WITHOUT issuing any store request. For a resolvable URI it performs a
     * metadata-only {@code statObject} (no byte transfer): object present -> {@code true}; object
     * missing ({@code NoSuchKey}) -> {@code false}. Any other storage failure (network/auth/IO) is
     * NOT swallowed into {@code false} — it propagates, because "the store is unreachable" must never
     * be reported the same as "the evidence is gone".
     */
    boolean exists(String storageUri);

    /**
     * Opens a byte stream for {@code storageUri}, honoring a single-range {@code Range} header
     * (`bytes=start-end` / `bytes=start-`) when present and satisfiable; otherwise returns the full
     * object. Throws {@link jakarta.persistence.EntityNotFoundException} when the URI scheme is
     * unreadable (e.g. {@code file://}) or the object is missing from the bucket — callers 404
     * either way (hidden, not a 500).
     */
    EvidenceObjectStream open(String storageUri, String rangeHeader);
}
