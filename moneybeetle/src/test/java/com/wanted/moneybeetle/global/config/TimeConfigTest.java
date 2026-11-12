package com.wanted.moneybeetle.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.trading.model.service.MarketHours;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   공통 시계와 시연용 현재 시각 APP_DEMO_NOW (#405)
*   - 비어 있으면 실제 시계 그대로 / 값이 있으면 그 시각에서 시작해 흐른다 / 형식이 틀리면 서버가 뜨지 않는다
*  */
class TimeConfigTest {

    /** 실제 시계 대신 쓰는 "지금" : 2026-11-12(목) 17:00 서울 */
    private static final Clock THURSDAY_5PM = Clock.fixed(
            LocalDateTime.of(2026, 11, 12, 17, 0).atZone(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);

    @Test
    @DisplayName("APP_DEMO_NOW 가 비어 있으면 실제 시계를 그대로 쓴다")
    void blankUsesSystemClock() {
        assertThat(TimeConfig.clockFrom(null, THURSDAY_5PM)).isSameAs(THURSDAY_5PM);
        assertThat(TimeConfig.clockFrom("", THURSDAY_5PM)).isSameAs(THURSDAY_5PM);
        assertThat(TimeConfig.clockFrom("   ", THURSDAY_5PM)).isSameAs(THURSDAY_5PM);
    }

    @Test
    @DisplayName("APP_DEMO_NOW 를 주면 지금을 그 시각(서울)으로 본다 : 월요일 19:50")
    void demoNowStartsAtGivenTime() {
        Clock clock = TimeConfig.clockFrom(" 2026-11-16T19:50 ", THURSDAY_5PM);

        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(now).isEqualTo(LocalDateTime.of(2026, 11, 16, 19, 50));
        assertThat(now.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(clock.getZone()).isEqualTo(TimeConfig.SEOUL);
    }

    @Test
    @DisplayName("멈춘 시계가 아니라 흐른다 : 19:50 에 띄우면 장이 열려 있고(주문), 11분 뒤에는 닫혀 있다(다음 턴 진행)")
    void demoClockKeepsTicking() {
        Clock start = TimeConfig.clockFrom("2026-11-16T19:50", THURSDAY_5PM);
        Clock elevenMinutesLater = Clock.offset(start, Duration.ofMinutes(11));   // 실제 시간이 11분 흐른 것과 같다

        assertThat(MarketHours.isOpen(LocalDateTime.now(start))).isTrue();
        assertThat(MarketHours.isOpen(LocalDateTime.now(elevenMinutesLater))).isFalse();
    }

    @Test
    @DisplayName("실제 시계와 함께 쓰면 시작 시각에서 실제 시간만큼 앞으로 간다")
    void demoClockFollowsRealTime() {
        Clock clock = TimeConfig.clockFrom("2026-11-16T09:00", Clock.system(TimeConfig.SEOUL));

        LocalDateTime first = LocalDateTime.now(clock);
        LocalDateTime second = LocalDateTime.now(clock);

        assertThat(Duration.between(LocalDateTime.of(2026, 11, 16, 9, 0), first)).isBetween(Duration.ZERO, Duration.ofSeconds(5));
        assertThat(second).isAfterOrEqualTo(first);
    }

    @Test
    @DisplayName("형식이 틀리면 엉뚱한 날짜로 뜨지 않도록 서버 시작을 멈추고 이유를 알려 준다")
    void wrongFormatStopsStartup() {
        for (String wrong : new String[] {"2026-11-16 19:50", "2026/11/16T19:50", "월요일 19시", "2026-11-16"}) {
            assertThatThrownBy(() -> TimeConfig.clockFrom(wrong, THURSDAY_5PM))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_DEMO_NOW")
                    .hasMessageContaining(wrong);
        }
    }

    @Test
    @DisplayName("스프링 설정 app.demo-now 로 주면 Clock 빈이 그 시각에서 시작한다, 없으면 실제 시각")
    void beanReadsProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(TimeConfig.class)
                .withPropertyValues("app.demo-now=2026-11-16T19:50")
                .run(context -> {
                    LocalDateTime now = LocalDateTime.now(context.getBean(Clock.class));
                    assertThat(Duration.between(LocalDateTime.of(2026, 11, 16, 19, 50), now))
                            .isBetween(Duration.ZERO, Duration.ofSeconds(5));
                });

        new ApplicationContextRunner()
                .withUserConfiguration(TimeConfig.class)
                .run(context -> {
                    LocalDateTime now = LocalDateTime.now(context.getBean(Clock.class));
                    assertThat(Duration.between(now, LocalDateTime.now(TimeConfig.SEOUL)).abs())
                            .isLessThan(Duration.ofSeconds(5));
                });
    }
}
