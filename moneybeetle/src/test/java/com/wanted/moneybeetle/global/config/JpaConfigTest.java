package com.wanted.moneybeetle.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   JpaConfig #114 확인 : created_at / updated_at 의 "지금 시간"이 주입한 Clock 을 따른다 (DP-42)
*  */
class JpaConfigTest {

    @Test
    void 기록_시각은_주입한_Clock_을_따른다() {
        // UTC 2026-11-09 00:30 = 서울 2026-11-09 09:30
        Clock fixed = Clock.fixed(Instant.parse("2026-11-09T00:30:00Z"), TimeConfig.SEOUL);

        DateTimeProvider provider = new JpaConfig().seoulDateTimeProvider(fixed);

        assertThat(provider.getNow()).contains(LocalDateTime.of(2026, 11, 9, 9, 30));
    }

    @Test
    void 서버_시간대가_UTC_여도_공통_Clock_이면_서울_시간() {
        Instant now = Instant.parse("2026-11-09T15:10:00Z");                // 서울은 다음 날 00:10
        Clock seoul = Clock.fixed(now, TimeConfig.SEOUL);

        DateTimeProvider provider = new JpaConfig().seoulDateTimeProvider(seoul);

        assertThat(provider.getNow()).contains(LocalDateTime.of(2026, 11, 10, 0, 10));
        assertThat(LocalDateTime.ofInstant(now, ZoneId.of("UTC"))).isEqualTo(LocalDateTime.of(2026, 11, 9, 15, 10));
    }
}
