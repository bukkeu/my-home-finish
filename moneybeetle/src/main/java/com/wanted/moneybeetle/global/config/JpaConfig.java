package com.wanted.moneybeetle.global.config;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   @EnableJpaAuditing -> BaseTimeEntity 의 created_at / updated_at 자동 기록
*   Repository·Entity 스캔은 따로 지정하지 않는다 (#114) : 시작 클래스(MoneyBeetleApplication)가 루트 패키지에 있어
*   스프링 부트가 com.wanted.moneybeetle 아래의 dao·entity 를 자동으로 찾는다
*   seoulDateTimeProvider -> 서버(JVM) 시간대가 UTC 여도 created_at / updated_at 을 서울 시간으로 기록 (DP-42)
*     #114 : 시각을 직접 구하지 않고 TimeConfig 의 공통 Clock(Asia/Seoul)을 주입받는다 → 테스트에서 시간을 고정할 수 있다
*  */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "seoulDateTimeProvider")  // 자동 시간 기록 기능 (서울 시간 기준)
public class JpaConfig {

    // @CreatedDate / @LastModifiedDate 에 넣을 "지금 시간" : 공통 Clock(서울 시간대) 기준
    @Bean
    public DateTimeProvider seoulDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
