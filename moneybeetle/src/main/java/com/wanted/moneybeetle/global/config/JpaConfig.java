package com.wanted.moneybeetle.global.config;

/* comment.
*   담당 : SCM (회원·공통)
*   @EnableJpaRepositories, @EntityScan (domain 하위 dao / entity 패키지)
*   @EnableJpaAuditing -> BaseTimeEntity 의 created_at / updated_at 자동 기록
 *  MoneyBeetleApplication 이 global.config 패키지에 있어서 기본 스캔 범위가 global.config 로 좁아진다
 *  → 루트 패키지를 직접 지정
*   seoulDateTimeProvider -> 서버(JVM) 시간대가 UTC 여도 created_at / updated_at 을 서울 시간으로 기록 (DP-42)
*  */

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "seoulDateTimeProvider")  // 자동 시간 기록 기능 (서울 시간 기준)
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle")
@EntityScan(basePackages = "com.wanted.moneybeetle")
public class JpaConfig {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // @CreatedDate / @LastModifiedDate 에 넣을 "지금 시간"을 서울 시간으로 만든다
    @Bean
    public DateTimeProvider seoulDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(SEOUL));
    }
}
