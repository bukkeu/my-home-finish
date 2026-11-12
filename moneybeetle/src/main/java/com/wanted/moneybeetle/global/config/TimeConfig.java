package com.wanted.moneybeetle.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   "오늘" 판단에 쓰는 공통 시계 (DP-42, Asia/Seoul)
*   - 출석, 청약 납입, 휴장일, 공고 상태처럼 날짜로 판단하는 규칙은 LocalDate.now() 를 직접 부르지 않고
*     이 Clock 을 주입받아 LocalDate.now(clock) 으로 쓴다
*   - 서버 시간대가 달라도 항상 서울 날짜가 나오고, 테스트에서는 Clock.fixed(...) 로 날짜를 고정할 수 있다
*   - 시연용 현재 시각 (#405) : 환경변수 APP_DEMO_NOW(app.demo-now)에 "2026-11-16T19:50" 처럼 서울 시각을 주면
*     서버가 뜬 순간을 그 시각으로 보고, 그 뒤로는 실제 시간만큼 흐른다 (멈춰 있는 시계가 아니다).
*     모의투자는 주문을 장 시간(08:00~20:00)에만, 다음 턴 진행을 장 마감 뒤에만 받으므로(#208), 예를 들어 19:50 으로 띄우면
*     10분 동안 주문을 보여 주고 20:00 이 지나면 턴 넘기기를 보여 줄 수 있다. 값이 비어 있으면 지금과 같은 실제 시계다
*  */
@Slf4j
@Configuration
public class TimeConfig {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock(@Value("${app.demo-now:}") String demoNow) {
        return clockFrom(demoNow, Clock.system(SEOUL));
    }

    /**
     * 시연용 시각이 비어 있으면 실제 시계(system)를 그대로, 있으면 지금 이 순간을 그 시각으로 보는 흐르는 시계를 돌려준다.
     * 형식이 틀리면 서버가 엉뚱한 날짜로 뜨지 않도록 시작을 멈춘다.
     */
    static Clock clockFrom(String demoNow, Clock system) {
        if (demoNow == null || demoNow.isBlank()) {
            return system;
        }
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(demoNow.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    "APP_DEMO_NOW 는 2026-11-16T19:50 처럼 '날짜T시각' 으로 써 주세요 (지금 값: " + demoNow + ")", e);
        }
        Duration offset = Duration.between(LocalDateTime.now(system), start);
        log.warn("시연용 시계를 씁니다 : 지금을 {} 로 보고 흐릅니다 (APP_DEMO_NOW). 실제 시각과 {}분 차이", start, offset.toMinutes());
        return Clock.offset(system, offset);
    }
}
