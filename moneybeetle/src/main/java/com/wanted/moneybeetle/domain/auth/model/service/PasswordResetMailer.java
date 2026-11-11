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
*   비밀번호 재설정 메일 발송 (USR-09, 이슈 #8)
*   - 요청 처리와 다른 스레드(mailExecutor)에서 보낸다. 메일 서버가 느리거나 막혀도 요청 응답 시간이 달라지지 않아,
*     응답 시간으로 가입된 이메일인지 알아내는 일을 막는다.
*   - 발송이 실패해도 예외를 밖으로 내지 않고 서버 로그에만 남긴다. 받는 사람·토큰은 로그에 남기지 않는다 (DP-02).
*     실패 원인은 예외(스택 포함)로 남기되 메시지의 주소는 가린다 (MaskedException).
*   - @Async 는 다른 빈이 부를 때만 동작하므로 PasswordResetService 와 별도 클래스로 둔다.
*  */
@Slf4j
@Component
public class PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String mailFrom;

    public PasswordResetMailer(JavaMailSender mailSender,
                               @Value("${app.base-url}") String baseUrl,
                               @Value("${app.mail-from}") String mailFrom) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.mailFrom = mailFrom;
    }

    @Async("mailExecutor")
    public void send(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("[머니비틀] 비밀번호 재설정 안내");
        message.setText("아래 주소에서 10분 안에 새 비밀번호를 정해 주세요. 한 번만 사용할 수 있어요.\n\n"
                + baseUrl + "/password-reset?token=" + token + "\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 원인을 찾을 수 있게 예외를 남기되, 메시지의 받는 사람 주소는 가린 복사본을 남긴다 (토큰은 예외에 실리지 않는다)
            log.error("비밀번호 재설정 메일 발송에 실패했어요", MaskedException.of(e));
        }
    }
}
