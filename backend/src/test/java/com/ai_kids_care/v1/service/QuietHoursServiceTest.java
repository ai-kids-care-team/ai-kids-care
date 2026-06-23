package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.service.QuietHoursService.QuietWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for quiet-hours evaluation (Asia/Seoul, cross-midnight, nextEnd, bad config). */
class QuietHoursServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final QuietHoursService svc = new QuietHoursService(null, new ObjectMapper());

    private OffsetDateTime seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toOffsetDateTime();
    }

    @Test
    void crossMidnightWindow_within() {
        QuietWindow w = svc.parse("{\"start\":\"22:00\",\"end\":\"07:00\"}").orElseThrow();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-23T23:30"))).isTrue();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-24T02:00"))).isTrue();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-23T12:00"))).isFalse();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-23T07:00"))).isFalse(); // end exclusive
    }

    @Test
    void sameDayWindow_within() {
        QuietWindow w = svc.parse("{\"start\":\"13:00\",\"end\":\"14:00\"}").orElseThrow();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-23T13:30"))).isTrue();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-23T14:30"))).isFalse();
        assertThat(svc.isWithinQuietHours(w, seoul("2026-06-23T12:59"))).isFalse();
    }

    @Test
    void nextEnd_crossMidnight_lateNight_isNextDayEnd() {
        QuietWindow w = svc.parse("{\"start\":\"22:00\",\"end\":\"07:00\"}").orElseThrow();
        OffsetDateTime end = svc.nextEndInstant(w, seoul("2026-06-23T23:30"));
        assertThat(end.toInstant()).isEqualTo(seoul("2026-06-24T07:00").toInstant());
    }

    @Test
    void nextEnd_crossMidnight_earlyMorning_isSameDayEnd() {
        QuietWindow w = svc.parse("{\"start\":\"22:00\",\"end\":\"07:00\"}").orElseThrow();
        OffsetDateTime end = svc.nextEndInstant(w, seoul("2026-06-24T02:00"));
        assertThat(end.toInstant()).isEqualTo(seoul("2026-06-24T07:00").toInstant());
    }

    @Test
    void nextEnd_sameDayWindow() {
        QuietWindow w = svc.parse("{\"start\":\"13:00\",\"end\":\"14:00\"}").orElseThrow();
        OffsetDateTime end = svc.nextEndInstant(w, seoul("2026-06-23T13:30"));
        assertThat(end.toInstant()).isEqualTo(seoul("2026-06-23T14:00").toInstant());
    }

    @Test
    void emptyOrInvalidConfig_isEmpty() {
        assertThat(svc.parse(null)).isEmpty();
        assertThat(svc.parse("")).isEmpty();
        assertThat(svc.parse("   ")).isEmpty();
        assertThat(svc.parse("{}")).isEmpty();
        assertThat(svc.parse("not-json")).isEmpty();
    }
}
