package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (#208)
*   MarketHours 확인 : 정규장 08:00~20:00, 경계 시각 (여는 시각 포함, 닫는 시각 불포함)
*  */
class MarketHoursTest {

    private static LocalDateTime at(String time) {
        return LocalDateTime.parse("2026-11-05T" + time);
    }

    @Test
    void 경계_시각은_07시59분59초_닫힘_08시_열림_19시59분59초_열림_20시_닫힘() {
        assertThat(MarketHours.isOpen(at("07:59:59"))).isFalse();
        assertThat(MarketHours.isOpen(at("08:00:00"))).isTrue();
        assertThat(MarketHours.isOpen(at("19:59:59"))).isTrue();
        assertThat(MarketHours.isOpen(at("20:00:00"))).isFalse();
    }

    @Test
    void 한낮은_열려_있고_밤과_새벽은_닫혀_있다() {
        assertThat(MarketHours.isOpen(at("12:00:00"))).isTrue();
        assertThat(MarketHours.isOpen(at("00:00:00"))).isFalse();
        assertThat(MarketHours.isOpen(at("23:59:59"))).isFalse();
    }

    @Test
    void 장_시작_시각은_그_거래일_08시다() {
        assertThat(MarketHours.opensAt(LocalDate.of(2026, 11, 6))).isEqualTo(LocalDateTime.of(2026, 11, 6, 8, 0));
    }
}
