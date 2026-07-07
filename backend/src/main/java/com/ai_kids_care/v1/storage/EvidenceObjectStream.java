package com.ai_kids_care.v1.storage;

import java.io.InputStream;

/**
 * Result of {@link EvidenceStoragePort#open}. The caller (the content controller) owns closing
 * {@code inputStream} once fully drained into the HTTP response body.
 *
 * @param inputStream  the (possibly partial) object byte stream; caller must close it
 * @param contentLength number of bytes {@code inputStream} will yield (full size, or the range's
 *                      byte count when {@code partial})
 * @param partial      whether this is a Range-satisfying partial response (HTTP 206) or the full
 *                      object (HTTP 200)
 * @param rangeStart   inclusive start byte of the served range (0 when not partial)
 * @param rangeEnd     inclusive end byte of the served range ({@code totalSize - 1} when not partial)
 * @param totalSize    total object size in bytes, regardless of how much of it is being served
 */
public record EvidenceObjectStream(
        InputStream inputStream,
        long contentLength,
        boolean partial,
        long rangeStart,
        long rangeEnd,
        long totalSize
) {
}
