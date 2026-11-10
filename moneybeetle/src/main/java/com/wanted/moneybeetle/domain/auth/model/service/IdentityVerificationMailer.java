package com.wanted.moneybeetle.domain.auth.model.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : DBA ② (회원·인증)
*   본인인증 코드 메일 발송 (USR-05, 이슈 #9)
*   - PasswordResetMailer 와 같은 방식: mailExecutor 스레드에서 보내 응답 시간이 메일 서버 상태에 좌우되지 않게 한다.
*   - 외부 연동(메일 서버) 실패는 1회만 자동으로 다시 시도한다 (EP-02). 그래도 실패하면 예외를 내지 않고 로그에만 남긴다.
*     받는 사람·코드는 로그에 남기지 않는다 (DP-02). 사용자는 60초 뒤 코드를 다시 요청할 수 있다.
*  */
@Slf4j
@Component
public class IdentityVerificationMailer {

    private static final int MAX_ATTEMPTS = 2;   // 처음 1회 + 자동 재시도 1회 (EP-02)

    private final JavaMailSender mailSender;
    private final String mailFrom;

    public IdentityVerificationMailer(JavaMailSender mailSender, @Value("${app.mail-from}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    @Async("mailExecutor")
    public void send(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("[머니비틀] 본인인증 번호 안내");
        message.setText("본인인증 번호는 " + code + " 입니다. 5분 안에 입력해 주세요.\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                mailSender.send(message);
                return;
            } catch (MailException e) {
                // 받는 사람·코드가 로그에 남지 않도록 예외 종류만 남긴다
                log.error("본인인증 메일 발송에 실패했어요 ({}/{}): {}", attempt, MAX_ATTEMPTS, e.getClass().getSimpleName());
            }
        }
    }
}
