package com.ai_kids_care.v1.storage;

/**
 * Thrown when a client sends a well-formed single-range {@code Range} header
 * (`bytes=start-end` / `bytes=start-`) whose bounds cannot be satisfied against the object's actual
 * size (start at or beyond the end of the object, or an inverted range) — RFC 7233 calls for
 * {@code 416 Range Not Satisfiable} in this case, not a silent fallback to a full {@code 200}.
 *
 * <p>Deliberately NOT raised for a header that is absent, unparseable, multi-range, or a suffix range
 * ({@code bytes=-500}) — those remain unsupported-but-ignored (full {@code 200}), unchanged from prior
 * behavior; see {@link MinioEvidenceStorageAdapter#parseRange}.
 */
public class UnsatisfiableRangeException extends RuntimeException {

    private final long totalSize;

    public UnsatisfiableRangeException(long totalSize) {
        super("Range header out of bounds for object of size " + totalSize);
        this.totalSize = totalSize;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
