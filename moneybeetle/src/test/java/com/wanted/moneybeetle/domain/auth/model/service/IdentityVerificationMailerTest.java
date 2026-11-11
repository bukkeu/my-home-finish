package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import com.wanted.moneybeetle.support.LogCapture;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/* comment.
*   담당 : DBA ② (회원·인증)
*   IdentityVerificationMailer (#9) - 메일 서버 없이 돈다
*  */
class IdentityVerificationMailerTest {

    private JavaMailSender mailSender;
    private IdentityVerificationMailer mailer;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailer = new IdentityVerificationMailer(mailSender, "no-reply@moneybeetle.local");
    }

    @Test
    @DisplayName("받는 사람에게 인증번호가 든 메일을 보낸다")
    void sendsCode() {
        mailer.send("minji@gmail.com", "123456");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getFrom()).isEqualTo("no-reply@moneybeetle.local");
        assertThat(mail.getValue().getTo()).containsExactly("minji@gmail.com");
        assertThat(mail.getValue().getText()).contains("123456");
    }

    @Test
    @DisplayName("첫 발송이 실패하면 1회만 다시 시도한다 (EP-02)")
    void retriesOnce() {
        doThrow(new MailSendException("smtp down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send("minji@gmail.com", "123456");

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("두 번 모두 실패해도 더 시도하지 않고, 예외를 밖으로 내지 않는다")
    void givesUpAfterOneRetry() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send("minji@gmail.com", "123456");

        verify(mailSender, Mockito.times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("발송은 요청과 다른 스레드(mailExecutor)에서 하도록 @Async 가 붙어 있다")
    void sendIsAsync() throws NoSuchMethodException {
        Method send = IdentityVerificationMailer.class.getMethod("send", String.class, String.class);

        assertThat(send.getAnnotation(Async.class)).isNotNull();
        assertThat(send.getAnnotation(Async.class).value()).isEqualTo("mailExecutor");
    }

    @Test
    @DisplayName("발송이 실패하면 원인을 error 로그로 남기되(스택 포함), 받는 사람 주소와 인증번호 은 로그 어디에도 없다 (#299)")
    void failureLogsMaskedCause() {
        doThrow(new MailSendException("Invalid Addresses: <minji@gmail.com> smtp down", new IllegalStateException("550 <minji@gmail.com> unknown")))
                .when(mailSender).send(any(SimpleMailMessage.class));

        try (LogCapture logs = LogCapture.of(IdentityVerificationMailer.class)) {
            mailer.send("minji@gmail.com", "482913");

            assertThat(logs.events()).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).doesNotContain("minji", "gmail", "482913");
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
                assertThat(event.getThrowableProxy().getMessage()).startsWith("org.springframework.mail.MailSendException: ")
                        .contains("<***>").doesNotContain("minji", "gmail", "482913");
                assertThat(event.getThrowableProxy().getCause().getMessage()).startsWith("java.lang.IllegalStateException: ")
                        .doesNotContain("minji", "gmail");
            });
        }
    }

    @Test
    @DisplayName("재시도까지 두 번 실패하면 시도 번호가 든 error 로그가 두 번 남는다 (#299)")
    void logsEveryAttempt() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        try (LogCapture logs = LogCapture.of(IdentityVerificationMailer.class)) {
            mailer.send("minji@gmail.com", "123456");

            assertThat(logs.events()).extracting(event -> event.getFormattedMessage())
                    .containsExactly("본인인증 메일 발송에 실패했어요 (1/2)", "본인인증 메일 발송에 실패했어요 (2/2)");
        }
    }
}
