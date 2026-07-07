package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.EventEvidenceFileService;
import com.ai_kids_care.v1.storage.EvidenceObjectStream;
import com.ai_kids_care.v1.storage.UnsatisfiableRangeException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@Tag(name = "EventEvidenceFile")
@RestController
@RequestMapping("/api/v1/event_evidence_files")
@RequiredArgsConstructor
public class EventEvidenceFileController {

    private final EventEvidenceFileService service;

    /**
     * D-STORE backend-proxied evidence content read (design.md §A: MinIO stays off the public
     * network). Every request re-resolves session + staff role + tenant + event ownership via
     * {@link EventEvidenceFileService#getContentMeta} BEFORE the object-store IO in
     * {@link EventEvidenceFileService#openContentStream} runs — the two-call split keeps the MinIO
     * round-trip outside any DB transaction. Supports a single-range {@code Range} header for
     * {@code <video>} seek (206 Partial Content); absent/unsupported Range falls back to a full 200;
     * a well-formed but out-of-bounds Range yields 416 (refine-evidence-readback-robustness).
     */
    @GetMapping("/{evidenceId}/content")
    public ResponseEntity<StreamingResponseBody> getContent(
            @PathVariable Long evidenceId,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        EventEvidenceFileService.ContentMeta meta = service.getContentMeta(evidenceId);
        EvidenceObjectStream stream;
        try {
            stream = service.openContentStream(meta, rangeHeader);
        } catch (UnsatisfiableRangeException e) {
            // RFC 7233 §4.4: 416 + Content-Range: bytes */<length>, empty (null) body — never fall
            // back to a full 200 for a Range the caller understood but couldn't honor. NOTE: the
            // method's return type must stay ResponseEntity<StreamingResponseBody> (not a wildcard) —
            // Spring's StreamingResponseBodyReturnValueHandler.supportsReturnType inspects the
            // METHOD'S DECLARED generic parameter via reflection (ResolvableType), not the runtime
            // value, to decide whether to route this handler method through async processing at all;
            // widening it to `<?>` would silently break the 200/206 branches' async dispatch too. A
            // null body here is handled synchronously by that same handler (no async start, headers
            // still written) — see its handleReturnValue null-body short-circuit.
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + e.getTotalSize())
                    .body(null);
        }

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = stream.inputStream()) {
                in.transferTo(outputStream);
            }
        };

        ResponseEntity.BodyBuilder builder = stream.partial()
                ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                : ResponseEntity.ok();
        builder.header(HttpHeaders.CONTENT_TYPE, meta.mimeType())
                // RFC 7232 §2.3: ETag validator MUST be a quoted-string.
                .header(HttpHeaders.ETAG, "\"" + meta.hash() + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(stream.contentLength()));
        if (stream.partial()) {
            builder.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + stream.rangeStart() + "-" + stream.rangeEnd() + "/" + stream.totalSize());
        }
        return builder.body(body);
    }
}
