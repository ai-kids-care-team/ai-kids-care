package com.ai_kids_care.v1.storage;

/**
 * Unchecked wrapper for MinIO SDK failures that are NOT "object doesn't exist" (that case maps to
 * {@link jakarta.persistence.EntityNotFoundException} / 404 instead — see
 * {@link MinioEvidenceStorageAdapter}). Genuine storage-layer failures (network, auth, server
 * error) surface as this exception and fall through to the default 500 handler; there is no
 * dedicated recovery path for them in this change.
 */
public class EvidenceStorageException extends RuntimeException {

    public EvidenceStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
