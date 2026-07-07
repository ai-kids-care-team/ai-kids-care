package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.service.EventEvidenceFileService;
import com.ai_kids_care.v1.storage.EvidenceObjectStream;
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
     * {@code <video>} seek (206 Partial Content); absent/unsupported Range falls back to a full 200.
     */
    @GetMapping("/{evidenceId}/content")
    public ResponseEntity<StreamingResponseBody> getContent(
            @PathVariable Long evidenceId,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        EventEvidenceFileService.ContentMeta meta = service.getContentMeta(evidenceId);
        EvidenceObjectStream stream = service.openContentStream(meta, rangeHeader);

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = stream.inputStream()) {
                in.transferTo(outputStream);
            }
        };

        ResponseEntity.BodyBuilder builder = stream.partial()
                ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                : ResponseEntity.ok();
        builder.header(HttpHeaders.CONTENT_TYPE, meta.mimeType())
                .header(HttpHeaders.ETAG, meta.hash())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(stream.contentLength()));
        if (stream.partial()) {
            builder.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + stream.rangeStart() + "-" + stream.rangeEnd() + "/" + stream.totalSize());
        }
        return builder.body(body);
    }
}
