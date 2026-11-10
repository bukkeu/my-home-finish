package com.wanted.moneybeetle.domain.member.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/* comment.
*   담당 : DBA ② (회원·인증)
*   예약 작업(@Scheduled)을 켠다 (이슈 #66). 지금 예약 작업은 MemberCleanupJob(탈퇴 개인정보 정리, 만료 토큰 삭제) 하나다.
*  */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
