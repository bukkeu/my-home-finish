package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.PasswordResetTokenRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.PasswordResetToken;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   AccountService (#8) - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class AccountServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 9, 0);

    private MemberRepository memberRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private AccountService service;
    private Member member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new AccountService(memberRepository, tokenRepository, passwordEncoder, clock);

        member = Member.signup("minji@gmail.com", "old-hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE", TODAY);
        ReflectionTestUtils.setField(member, "memberId", 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findByEmail("minji@gmail.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.encode("beetle5678")).thenReturn("new-hash");
        when(passwordEncoder.matches("beetle1234", "old-hash")).thenReturn(true);
    }

    private static void assertCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    // ---- 아이디(이메일) 찾기 ----

    @Test
    @DisplayName("닉네임과 생년월일이 맞으면 이메일을 일부 가려서 알려 준다")
    void findsMaskedEmail() {
        when(memberRepository.findByNicknameAndBirthDate("민지", LocalDate.of(1999, 4, 12))).thenReturn(Optional.of(member));

        assertThat(service.findMaskedEmail("민지", LocalDate.of(1999, 4, 12)).maskedEmail()).isEqualTo("mi***@gmail.com");
    }

    @Test
    @DisplayName("맞는 회원이 없거나 탈퇴한 회원이면 MEMBER_NOT_FOUND 로 이메일을 알려 주지 않는다")
    void findEmailHidesInactive() {
        when(memberRepository.findByNicknameAndBirthDate("민지", LocalDate.of(1999, 4, 12))).thenReturn(Optional.of(member));
        member.withdraw(NOW);

        assertThatThrownBy(() -> service.findMaskedEmail("민지", LocalDate.of(1999, 4, 12)))
                .satisfies(e -> assertCode(e, MemberErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> service.findMaskedEmail("없는닉", LocalDate.of(1999, 4, 12)))
                .satisfies(e -> assertCode(e, MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("이메일 가리기: 앞 2글자만 보이고, 아주 짧으면 1글자만 보인다")
    void masksEmail() {
        assertThat(AccountService.maskEmail("minji@gmail.com")).isEqualTo("mi***@gmail.com");
        assertThat(AccountService.maskEmail("ab@x.kr")).isEqualTo("a***@x.kr");
        assertThat(AccountService.maskEmail("noatsign")).isEqualTo("***");
    }

    // ---- 비밀번호 재설정 토큰 발급 ----

    @Test
    @DisplayName("가입된 정상 회원이면 이전 토큰을 쓸 수 없게 하고 10분짜리 새 토큰을 저장한다")
    void issuesToken() {
        Optional<String> to = service.issuePasswordResetToken("  MinJi@Gmail.com ", "hash-1");

        assertThat(to).contains("minji@gmail.com");
        verify(tokenRepository).invalidateUnused(eq(1L), any(LocalDateTime.class));
        org.mockito.ArgumentCaptor<PasswordResetToken> saved = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo("hash-1");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    @DisplayName("없는 이메일·탈퇴 회원·비밀번호 없는 계정이면 토큰을 저장하지 않고 빈 값이다")
    void noTokenForUnknown() {
        assertThat(service.issuePasswordResetToken("nobody@gmail.com", "h")).isEmpty();
        assertThat(service.issuePasswordResetToken("  ", "h")).isEmpty();
        assertThat(service.issuePasswordResetToken(null, "h")).isEmpty();

        ReflectionTestUtils.setField(member, "passwordHash", null);
        assertThat(service.issuePasswordResetToken("minji@gmail.com", "h")).isEmpty();

        ReflectionTestUtils.setField(member, "passwordHash", "old-hash");
        member.withdraw(NOW);
        assertThat(service.issuePasswordResetToken("minji@gmail.com", "h")).isEmpty();

        verify(tokenRepository, never()).save(any());
    }

    // ---- 비밀번호 재설정 ----

    private PasswordResetToken validToken() {
        PasswordResetToken token = PasswordResetToken.issue(1L, "hash-1", NOW.minusMinutes(5));
        when(tokenRepository.findByTokenHashForUpdate("hash-1")).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    @DisplayName("유효한 토큰이면 비밀번호를 BCrypt 해시로 바꾸고 토큰을 사용 처리한다")
    void resetsPassword() {
        PasswordResetToken token = validToken();

        service.resetPassword("hash-1", "beetle5678", "beetle5678");

        assertThat(member.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    @DisplayName("10분이 지난 토큰은 TOKEN_EXPIRED, 이미 쓴 토큰은 TOKEN_USED, 없는 토큰은 TOKEN_EXPIRED")
    void tokenRules() {
        PasswordResetToken expired = PasswordResetToken.issue(1L, "hash-old", NOW.minusMinutes(10));
        when(tokenRepository.findByTokenHashForUpdate("hash-old")).thenReturn(Optional.of(expired));
        PasswordResetToken used = validToken();
        used.markUsed(NOW.minusMinutes(1));

        assertThatThrownBy(() -> service.resetPassword("hash-old", "beetle5678", "beetle5678"))
                .satisfies(e -> assertCode(e, MemberErrorCode.TOKEN_EXPIRED));
        assertThatThrownBy(() -> service.resetPassword("hash-1", "beetle5678", "beetle5678"))
                .satisfies(e -> assertCode(e, MemberErrorCode.TOKEN_USED));
        assertThatThrownBy(() -> service.resetPassword("nothing", "beetle5678", "beetle5678"))
                .satisfies(e -> assertCode(e, MemberErrorCode.TOKEN_EXPIRED));
        assertThat(member.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    @DisplayName("새 비밀번호가 틀리면 토큰을 쓰지 않고 막는다 (PASSWORD_INVALID, PASSWORD_MISMATCH)")
    void badNewPasswordKeepsToken() {
        PasswordResetToken token = validToken();

        assertThatThrownBy(() -> service.resetPassword("hash-1", "short", "short"))
                .satisfies(e -> assertCode(e, MemberErrorCode.PASSWORD_INVALID));
        assertThatThrownBy(() -> service.resetPassword("hash-1", "beetle5678", "beetle0000"))
                .satisfies(e -> assertCode(e, MemberErrorCode.PASSWORD_MISMATCH));
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰으로는 재설정할 수 없다")
    void withdrawnMemberCannotReset() {
        validToken();
        member.withdraw(NOW);

        assertThatThrownBy(() -> service.resetPassword("hash-1", "beetle5678", "beetle5678"))
                .satisfies(e -> assertCode(e, MemberErrorCode.TOKEN_EXPIRED));
    }

    // ---- 비밀번호 변경 ----

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 바꾼다")
    void changesPassword() {
        service.changePassword(1L, "beetle1234", "beetle5678", "beetle5678");

        assertThat(member.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 PASSWORD_WRONG 이고 바뀌지 않는다. 새 비밀번호 검사보다 먼저다")
    void wrongCurrentPassword() {
        assertThatThrownBy(() -> service.changePassword(1L, "wrong", "short", "short"))
                .satisfies(e -> assertCode(e, MemberErrorCode.PASSWORD_WRONG));
        assertThatThrownBy(() -> service.changePassword(1L, "beetle1234", "short", "short"))
                .satisfies(e -> assertCode(e, MemberErrorCode.PASSWORD_INVALID));
        assertThatThrownBy(() -> service.changePassword(1L, "beetle1234", "beetle5678", "beetle0000"))
                .satisfies(e -> assertCode(e, MemberErrorCode.PASSWORD_MISMATCH));
        assertThat(member.getPasswordHash()).isEqualTo("old-hash");
    }

    // ---- 탈퇴 ----

    @Test
    @DisplayName("비밀번호가 맞으면 계정을 WITHDRAWN 으로 바꾸고 탈퇴 시각을 남긴다 (개인정보는 그대로, 30일 뒤 파기)")
    void withdraws() {
        service.withdraw(1L, "beetle1234");

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
        assertThat(member.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(member.getEmail()).isEqualTo("minji@gmail.com");
    }

    @Test
    @DisplayName("탈퇴 때 비밀번호가 틀리면 PASSWORD_WRONG 이고 계정은 그대로다. 관리자는 FORBIDDEN")
    void withdrawGuards() {
        assertThatThrownBy(() -> service.withdraw(1L, "wrong"))
                .satisfies(e -> assertCode(e, MemberErrorCode.PASSWORD_WRONG));
        assertThat(member.isActive()).isTrue();

        ReflectionTestUtils.setField(member, "role", Member.ROLE_ADMIN);
        assertThatThrownBy(() -> service.withdraw(1L, "beetle1234"))
                .satisfies(e -> assertCode(e, CommonErrorCode.FORBIDDEN));
        assertThat(member.isActive()).isTrue();
    }

    @Test
    @DisplayName("없는 회원이면 MEMBER_NOT_FOUND")
    void memberNotFound() {
        when(memberRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(9L, "beetle1234"))
                .satisfies(e -> assertCode(e, MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
