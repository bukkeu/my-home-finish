package com.wanted.moneybeetle.domain.auth.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/* comment.
*   담당 : DBA ② (회원·인증)
*   메일을 요청과 따로 보내기 위한 설정 (PasswordResetMailer 의 @Async("mailExecutor"), 이슈 #8)
*   - 스레드 2개, 대기 100건. 대기열이 가득 차면 거절한다(AbortPolicy). 요청 스레드가 직접 보내면(CallerRunsPolicy) 메일 서버 장애 때 요청 스레드가 막히기 때문이다.
*     거절은 PasswordResetService 가 받아 새 토큰을 지우고 로그만 남긴다 (응답은 그대로 202).
*   - 서버가 꺼질 때 보내는 중인 메일은 최대 10초까지 기다린다.
*  */
@Configuration
@EnableAsync
public class MailAsyncConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());   // 가득 차면 TaskRejectedException - 요청 스레드가 메일을 직접 보내지 않는다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
