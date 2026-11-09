package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

/* comment.
*   담당 : DBA ② (회원·인증)
*   PasswordResetService (#8) - 메일 서버·DB 없이 돈다
*  */
class PasswordResetServiceTest {

    private AccountService accountService;
    private PasswordResetMailer mailer;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        mailer = mock(PasswordResetMailer.class);
        service = new PasswordResetService(accountService, mailer);
    }

    @Test
    @DisplayName("가입된 회원이면 토큰 해시를 저장하고, 메일러에는 토큰 원문을 넘긴다 (원문은 저장하지 않는다)")
    void sendsMailWithRawToken() {
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        when(accountService.issuePasswordResetToken(anyString(), hashCaptor.capture())).thenReturn(Optional.of("minji@gmail.com"));

        service.requestReset("minji@gmail.com");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(org.mockito.ArgumentMatchers.eq("minji@gmail.com"), tokenCaptor.capture());
        String token = tokenCaptor.getValue();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(hashCaptor.getValue()).isEqualTo(PasswordResetService.hash(token)).isNotEqualTo(token).hasSize(64);
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 메일을 요청하지 않고 예외도 내지 않는다")
    void noMailForUnknownEmail() {
        when(accountService.issuePasswordResetToken(anyString(), anyString())).thenReturn(Optional.empty());

        service.requestReset("nobody@gmail.com");

        verify(mailer, never()).send(anyString(), anyString());
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
