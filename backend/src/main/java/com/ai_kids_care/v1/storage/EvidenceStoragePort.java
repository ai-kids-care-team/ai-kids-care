package com.ai_kids_care.v1.storage;

/**
 * D-STORE outbound port for reading evidence bytes out of object storage (MinIO in production).
 * Kept as a narrow interface (not a repository, not a service) so the MinIO SDK never leaks past
 * {@link MinioEvidenceStorageAdapter} — see design.md §"组件落点".
 */
public interface EvidenceStoragePort {

    /**
     * Cheap, no-network availability check used by the evidence LIST endpoint (called once per row,
     * so a real object-storage round trip per row is avoided). Resolves the {@code storage_uri}
     * scheme only: legacy {@code file://} rows (written before MinIO was wired in) are always
     * unavailable; {@code s3://} and bare-key rows are optimistically available (a since-deleted
     * object still 404s from {@link #open}, just not surfaced here).
     */
    boolean isAvailable(String storageUri);

    /**
     * Opens a byte stream for {@code storageUri}, honoring a single-range {@code Range} header
     * (`bytes=start-end` / `bytes=start-`) when present and satisfiable; otherwise returns the full
     * object. Throws {@link jakarta.persistence.EntityNotFoundException} when the URI scheme is
     * unreadable (e.g. {@code file://}) or the object is missing from the bucket — callers 404
     * either way (hidden, not a 500).
     */
    EvidenceObjectStream open(String storageUri, String rangeHeader);
}
