package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/* comment.
*   담당 : DBA ② (회원·인증)
*   PasswordResetService (#8) - 메일 서버·DB 없이 돈다
*  */
class PasswordResetServiceTest {

    private AccountService accountService;
    private JavaMailSender mailSender;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        mailSender = mock(JavaMailSender.class);
        service = new PasswordResetService(accountService, mailSender, "http://localhost:8080", "no-reply@moneybeetle.local");
    }

    @Test
    @DisplayName("가입된 회원이면 토큰 해시를 저장하고, 메일에는 토큰 원문이 든 링크를 보낸다 (원문은 저장하지 않는다)")
    void sendsMailWithRawToken() {
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        when(accountService.issuePasswordResetToken(anyString(), hashCaptor.capture())).thenReturn(Optional.of("minji@gmail.com"));

        service.requestReset("minji@gmail.com");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("minji@gmail.com");
        String text = mail.getValue().getText();
        String token = text.substring(text.indexOf("token=") + 6).lines().findFirst().orElseThrow();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(hashCaptor.getValue()).isEqualTo(PasswordResetService.hash(token)).isNotEqualTo(token).hasSize(64);
        assertThat(text).contains("http://localhost:8080/password-reset?token=");
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 메일을 보내지 않고 예외도 내지 않는다")
    void noMailForUnknownEmail() {
        when(accountService.issuePasswordResetToken(anyString(), anyString())).thenReturn(Optional.empty());

        service.requestReset("nobody@gmail.com");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("메일 발송이 실패해도 호출한 쪽에는 예외를 내지 않는다 (가입 여부·서버 상태를 알려 주지 않는다)")
    void mailFailureIsSwallowed() {
        when(accountService.issuePasswordResetToken(anyString(), anyString())).thenReturn(Optional.of("minji@gmail.com"));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        service.requestReset("minji@gmail.com");
    }

    @Test
    @DisplayName("재설정할 때는 받은 토큰 원문을 해시해서 넘긴다")
    void confirmHashesToken() {
        service.confirmReset("raw-token", "beetle5678", "beetle5678");

        verify(accountService).resetPassword(PasswordResetService.hash("raw-token"), "beetle5678", "beetle5678");
    }

    @Test
    @DisplayName("같은 토큰은 같은 해시, 다른 토큰은 다른 해시")
    void hashIsStable() {
        assertThat(PasswordResetService.hash("a")).isEqualTo(PasswordResetService.hash("a"));
        assertThat(PasswordResetService.hash("a")).isNotEqualTo(PasswordResetService.hash("b"));
    }
}
