package com.wanted.moneybeetle.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/* comment.
*   담당 : SCM (회원·공통)
*   "오늘" 판단에 쓰는 공통 시계 (DP-42, Asia/Seoul)
*   - 출석, 청약 납입, 휴장일, 공고 상태처럼 날짜로 판단하는 규칙은 LocalDate.now() 를 직접 부르지 않고
*     이 Clock 을 주입받아 LocalDate.now(clock) 으로 쓴다
*   - 서버 시간대가 달라도 항상 서울 날짜가 나오고, 테스트에서는 Clock.fixed(...) 로 날짜를 고정할 수 있다
*  */
@Configuration
public class TimeConfig {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SEOUL);
    }
}
