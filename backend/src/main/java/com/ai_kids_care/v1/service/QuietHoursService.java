package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Step ③b: resolves a kindergarten-wide quiet-hours window and evaluates whether a given instant
 * falls within it (Asia/Seoul, cross-midnight aware). The window is stored as
 * {@code kindergartens.notification_quiet_hours_json} = {@code {"start":"HH:mm","end":"HH:mm"}};
 * an absent/blank/unparseable value means "no quiet hours" (never defer). The resolve method carries
 * a guardianUserId parameter reserved for a future per-guardian override — this slice resolves only
 * the kindergarten-level window.
 */
@Service
@RequiredArgsConstructor
public class QuietHoursService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul"); // UTC+9, no DST

    private static final Logger log = LoggerFactory.getLogger(QuietHoursService.class);

    private final KindergartenRepository kindergartenRepository;
    private final ObjectMapper objectMapper;

    /** A quiet window in Asia/Seoul local time. */
    public record QuietWindow(LocalTime start, LocalTime end) {
        /** Whether the given Asia/Seoul local time is within the window (handles cross-midnight). */
        public boolean contains(LocalTime t) {
            if (start.equals(end)) {
                return false; // degenerate/empty window → never quiet
            }
            if (start.isBefore(end)) {
                return !t.isBefore(start) && t.isBefore(end); // same-day [start, end)
            }
            return !t.isBefore(start) || t.isBefore(end); // cross-midnight: [start,24:00) ∪ [00:00,end)
        }
    }

    /** Resolve the quiet window for a guardian. guardianUserId reserved for future per-guardian override. */
    public Optional<QuietWindow> resolveQuietWindow(Long kindergartenId, Long guardianUserId) {
        String json = kindergartenRepository.findById(kindergartenId)
                .map(k -> k.getQuietHoursJson())
                .orElse(null);
        return parse(json);
    }

    /** Parse a {@code {"start","end"}} window; blank/null/invalid → empty (never quiet). */
    public Optional<QuietWindow> parse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode start = node.get("start");
            JsonNode end = node.get("end");
            if (start == null || end == null || start.isNull() || end.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new QuietWindow(LocalTime.parse(start.asText()), LocalTime.parse(end.asText())));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Invalid quiet_hours_json '{}' — treating as no quiet hours: {}", json, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Whether the instant falls within the quiet window (Asia/Seoul local time). */
    public boolean isWithinQuietHours(QuietWindow window, OffsetDateTime at) {
        return window.contains(at.atZoneSameInstant(ZONE).toLocalTime());
    }

    /** The instant at which the current quiet window ends, for an instant known to be within it. */
    public OffsetDateTime nextEndInstant(QuietWindow window, OffsetDateTime at) {
        ZonedDateTime seoul = at.atZoneSameInstant(ZONE);
        LocalTime t = seoul.toLocalTime();
        ZonedDateTime end;
        if (window.start().isBefore(window.end())) {
            end = seoul.toLocalDate().atTime(window.end()).atZone(ZONE);            // same-day window
        } else if (t.isBefore(window.end())) {
            end = seoul.toLocalDate().atTime(window.end()).atZone(ZONE);            // cross-midnight, early-morning tail → today's end
        } else {
            end = seoul.toLocalDate().plusDays(1).atTime(window.end()).atZone(ZONE); // cross-midnight, late-night head → tomorrow's end
        }
        return end.toOffsetDateTime();
    }
}
