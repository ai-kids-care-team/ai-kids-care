package com.ai_kids_care.v1.storage;

import com.ai_kids_care.v1.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D-STORE MinIO adapter (design.md §A "后端反代，非 presigned"). MinIO stays off the public
 * network; every content read goes through {@link com.ai_kids_care.v1.service.EventEvidenceFileService}'s
 * per-request re-authorization before this class is ever invoked (see that class's javadoc for the
 * two-call split that keeps this IO outside any DB transaction).
 */
@Component
@RequiredArgsConstructor
public class MinioEvidenceStorageAdapter implements EvidenceStoragePort {

    /** Matches a single-range {@code bytes=start-end} or open-ended {@code bytes=start-}. */
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public boolean isAvailable(String storageUri) {
        return resolveKey(storageUri).isPresent();
    }

    @Override
    public EvidenceObjectStream open(String storageUri, String rangeHeader) {
        String key = resolveKey(storageUri)
                .orElseThrow(() -> new EntityNotFoundException("Evidence storage_uri is not readable (legacy file:// row)"));

        long totalSize = statSize(key);
        long[] range = parseRange(rangeHeader, totalSize);
        boolean partial = range != null;
        long rangeStart = partial ? range[0] : 0;
        long rangeEnd = partial ? range[1] : totalSize - 1;

        try {
            GetObjectArgs.Builder argsBuilder = GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key);
            if (partial) {
                argsBuilder = argsBuilder.offset(rangeStart).length(rangeEnd - rangeStart + 1);
            }
            var response = minioClient.getObject(argsBuilder.build());
            long contentLength = rangeEnd - rangeStart + 1;
            return new EvidenceObjectStream(response, contentLength, partial, rangeStart, rangeEnd, totalSize);
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
                throw new EntityNotFoundException("Evidence object not found in storage");
            }
            throw new EvidenceStorageException("MinIO getObject failed", e);
        } catch (MinioException e) {
            throw new EvidenceStorageException("MinIO getObject failed", e);
        }
    }

    private long statSize(String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(properties.getBucket()).object(key).build());
            return stat.size();
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
                throw new EntityNotFoundException("Evidence object not found in storage");
            }
            throw new EvidenceStorageException("MinIO statObject failed", e);
        } catch (MinioException e) {
            throw new EvidenceStorageException("MinIO statObject failed", e);
        }
    }

    /**
     * Parses a single-range {@code Range: bytes=start-end} / {@code bytes=start-} header against a
     * known total size. Returns {@code null} (full-content fallback) for an absent, malformed,
     * multi-range, or suffix-range ({@code bytes=-500}) header, or an out-of-bounds start — an
     * unsupported Range is safe to ignore per HTTP semantics, so callers just serve 200 instead of
     * rejecting the request.
     */
    static long[] parseRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || totalSize <= 0) {
            return null;
        }
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            long start = Long.parseLong(matcher.group(1));
            String endGroup = matcher.group(2);
            long end = endGroup.isEmpty() ? totalSize - 1 : Long.parseLong(endGroup);
            if (start < 0 || start >= totalSize || end < start) {
                return null;
            }
            end = Math.min(end, totalSize - 1);
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves {@code storage_uri} to an object-storage key (design.md §B): {@code s3://bucket/key}
     * -> {@code key} (the URI's bucket segment is IGNORED — the configured {@code MINIO_BUCKET} is
     * the source of truth); a bare key (no {@code ://}) -> used as-is; {@code file://...} (pre-MinIO
     * AI local-disk writes) and any other unrecognized scheme -> unresolvable (evidence unavailable
     * from this backend).
     */
    static Optional<String> resolveKey(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return Optional.empty();
        }
        if (storageUri.startsWith("file://")) {
            return Optional.empty();
        }
        if (storageUri.startsWith("s3://")) {
            String withoutScheme = storageUri.substring("s3://".length());
            int slash = withoutScheme.indexOf('/');
            if (slash < 0 || slash == withoutScheme.length() - 1) {
                return Optional.empty(); // s3://bucket with no key segment
            }
            return Optional.of(withoutScheme.substring(slash + 1));
        }
        if (storageUri.contains("://")) {
            return Optional.empty(); // unrecognized scheme (e.g. http://) -> unavailable
        }
        return Optional.of(storageUri); // bare key
    }
}
