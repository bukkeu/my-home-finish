package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/* comment.
*   담당 : DBA ② (회원·인증)
*   PasswordResetMailer (#8) - 메일 서버 없이 돈다
*  */
class PasswordResetMailerTest {

    private JavaMailSender mailSender;
    private PasswordResetMailer mailer;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailer = new PasswordResetMailer(mailSender, "http://localhost:8080", "no-reply@moneybeetle.local");
    }

    @Test
    @DisplayName("받는 사람에게 토큰 원문이 든 재설정 링크를 보낸다")
    void sendsLink() {
        mailer.send("minji@gmail.com", "raw-token");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getFrom()).isEqualTo("no-reply@moneybeetle.local");
        assertThat(mail.getValue().getTo()).containsExactly("minji@gmail.com");
        assertThat(mail.getValue().getText()).contains("http://localhost:8080/password-reset?token=raw-token");
    }

    @Test
    @DisplayName("메일 발송이 실패해도 예외를 밖으로 내지 않는다")
    void failureIsSwallowed() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send("minji@gmail.com", "raw-token");
    }

    @Test
    @DisplayName("발송은 요청과 다른 스레드(mailExecutor)에서 하도록 @Async 가 붙어 있다")
    void sendIsAsync() throws NoSuchMethodException {
        Method send = PasswordResetMailer.class.getMethod("send", String.class, String.class);

        assertThat(send.getAnnotation(Async.class)).isNotNull();
        assertThat(send.getAnnotation(Async.class).value()).isEqualTo("mailExecutor");
    }
}
