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
*   - 스레드 2개, 대기 100건. 대기열이 가득 차면 새 요청을 거절하지 않고 요청 스레드가 직접 보낸다(CallerRunsPolicy) - 메일이 사라지지 않는 쪽을 택했다.
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
