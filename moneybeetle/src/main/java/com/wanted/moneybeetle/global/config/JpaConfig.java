package com.wanted.moneybeetle.global.config;

/* comment.
*   담당 : SCM (회원·공통)
*   @EnableJpaRepositories, @EntityScan (domain 하위 dao / entity 패키지)
*   @EnableJpaAuditing -> BaseTimeEntity 의 created_at / updated_at 자동 기록
*  */

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing  // 자동 시간 기록 기능
@EnableJpaRepositories(basePackages = "com.wanted.moneybeetle")
@EntityScan(basePackages = "com.wanted.moneybeetle")
public class JpaConfig {

}
