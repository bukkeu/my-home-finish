package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SocialSignupDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.service.MemberSignupService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   SocialLoginService (이슈 #9) - 가짜 서비스와 세션으로 서버·DB 없이 돈다
*  */
class SocialLoginServiceTest {

    private final MemberService memberService = mock(MemberService.class);
    private final MemberSignupService memberSignupService = mock(MemberSignupService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockHttpSession session = new MockHttpSession();
    @SuppressWarnings("unchecked")
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository>
            clientRegistrations = mock(org.springframework.beans.factory.ObjectProvider.class);
    private final ReauthClock clock = new ReauthClock(java.time.Instant.parse("2026-11-10T00:00:00Z"));
    private SocialLoginService service;

    private final SocialProfile profile = new SocialProfile("GOOGLE", "g-1", "Minji@Gmail.com", "민지");

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(memberService, memberSignupService, authService, clientRegistrations, clock);
    }

    private void givenGoogleRegistered() {
        org.springframework.security.oauth2.client.registration.ClientRegistration google =
                org.springframework.security.config.oauth2.client.CommonOAuth2Provider.GOOGLE.getBuilder("google")
                        .clientId("id").clientSecret("secret").build();
        org.mockito.Mockito.when(clientRegistrations.getIfAvailable()).thenReturn(
                new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(google));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("등록된 제공자(구글)는 통과한다")
    void registeredProviderPasses() {
        givenGoogleRegistered();

        org.assertj.core.api.Assertions.assertThatCode(() -> service.requireAvailableProvider("google")).doesNotThrowAnyException();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("키가 없어 등록되지 않은 제공자·지원하지 않는 제공자·등록 정보가 아예 없으면 OAUTH_PROVIDER_UNAVAILABLE")
    void unavailableProviders() {
        givenGoogleRegistered();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireAvailableProvider("kakao"))   // 카카오 키가 없다
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.OAUTH_PROVIDER_UNAVAILABLE));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireAvailableProvider("naver"))
                .isInstanceOf(BusinessException.class);

        org.mockito.Mockito.when(clientRegistrations.getIfAvailable()).thenReturn(null);   // 키가 하나도 없어 등록 정보가 없다
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireAvailableProvider("google"))
                .isInstanceOf(BusinessException.class);
    }

    /** 시간을 앞으로 보낼 수 있는 시계 */
    private static class ReauthClock extends java.time.Clock {
        private java.time.Instant now;

        ReauthClock(java.time.Instant start) {
            this.now = start;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneId.of("Asia/Seoul");
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }

    // ---- 본인 확인(다시 로그인): 비밀번호가 없는 간편 로그인 회원의 탈퇴 ----

    @Test
    @DisplayName("같은 계정으로 다시 로그인하면 본인 확인이 기록되고, 탈퇴 때 한 번만 쓸 수 있다")
    void reauthSucceedsAndIsConsumedOnce() {
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_ACTIVE)));
        service.beginReauth(7L, session);
        assertThat(service.isReauthPending(session)).isTrue();

        LoginMemberDTO member = service.completeReauth(profile, session);

        assertThat(member.getMemberId()).isEqualTo(7L);
        assertThat(service.isReauthPending(session)).isFalse();   // 시작한 상태는 한 번 쓰고 지운다
        assertThat(service.consumeRecentReauth(7L, session)).isTrue();
        assertThat(service.consumeRecentReauth(7L, session)).isFalse();   // 확인은 한 번만 쓸 수 있다
    }

    @Test
    @DisplayName("다른 회원의 계정이거나 연결되지 않은 계정으로 확인하면 OAUTH_REAUTH_MISMATCH 이고 확인은 기록되지 않는다")
    void reauthWithOtherAccountIsRejected() {
        service.beginReauth(8L, session);   // 8번 회원이 확인하러 갔는데 7번 회원의 구글 계정으로 돌아왔다
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_ACTIVE)));
        assertCode(() -> service.completeReauth(profile, session), MemberErrorCode.OAUTH_REAUTH_MISMATCH);
        assertThat(service.consumeRecentReauth(8L, session)).isFalse();
        assertThat(service.consumeRecentReauth(7L, session)).isFalse();

        service.beginReauth(7L, session);
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.empty());   // 연결되지 않은 계정
        assertCode(() -> service.completeReauth(profile, session), MemberErrorCode.OAUTH_REAUTH_MISMATCH);
        assertThat(service.consumeRecentReauth(7L, session)).isFalse();
    }

    @Test
    @DisplayName("본인 확인을 시작한 적이 없거나 5분이 지났으면 OAUTH_SESSION_EXPIRED 이다")
    void reauthWithoutStartOrExpired() {
        assertCode(() -> service.completeReauth(profile, session), MemberErrorCode.OAUTH_SESSION_EXPIRED);
        assertCode(() -> service.completeReauth(profile, null), MemberErrorCode.OAUTH_SESSION_EXPIRED);

        service.beginReauth(7L, session);
        clock.advance(SocialLoginService.REAUTH_VALID);
        assertThat(service.isReauthPending(session)).isFalse();
        assertCode(() -> service.completeReauth(profile, session), MemberErrorCode.OAUTH_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("마친 본인 확인도 5분이 지나면 쓸 수 없고, 다른 회원은 쓸 수 없다. 세션이 없으면 false")
    void doneReauthExpiresAndIsPerMember() {
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_ACTIVE)));
        service.beginReauth(7L, session);
        service.completeReauth(profile, session);

        assertThat(service.consumeRecentReauth(8L, session)).isFalse();   // 다른 회원
        clock.advance(SocialLoginService.REAUTH_VALID);
        assertThat(service.consumeRecentReauth(7L, session)).isFalse();   // 시간이 지났다
        assertThat(service.consumeRecentReauth(7L, null)).isFalse();
    }

    @Test
    @DisplayName("본인 확인을 다시 시작하면 이전에 마친 확인은 지워지고, cancelReauth 는 시작한 상태를 지운다")
    void beginClearsPreviousAndCancelClearsPending() {
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_ACTIVE)));
        service.beginReauth(7L, session);
        service.completeReauth(profile, session);

        service.beginReauth(7L, session);
        assertThat(service.consumeRecentReauth(7L, session)).isFalse();

        service.cancelReauth(session);
        assertThat(service.isReauthPending(session)).isFalse();
        service.cancelReauth(null);   // 세션이 없어도 안전하다
    }

    private static LoginMemberDTO loginMember(String status) {
        Member member = Member.signupSocial("minji@gmail.com", "민지", LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        ReflectionTestUtils.setField(member, "status", status);
        return LoginMemberDTO.from(member, Member.ROLE_MEMBER);
    }

    private static void assertCode(Runnable action, MemberErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("이미 연결된 정상 회원이면 로그인하고 로그인 기록을 남긴다")
    void existingMemberLogsIn() {
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_ACTIVE)));
        session.setAttribute(SocialLoginService.SESSION_KEY, profile);   // 이전에 가입하다 만 정보가 남아 있어도 지운다

        SocialLoginService.Result result = service.login(profile, session);

        assertThat(result.signupRequired()).isFalse();
        assertThat(result.member().getMemberId()).isEqualTo(7L);
        verify(authService).recordLoginSuccess(7L);
        assertThat(session.getAttribute(SocialLoginService.SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("연결된 회원이 탈퇴·정지 상태면 로그인하지 않고 각각 ACCOUNT_WITHDRAWN / ACCOUNT_SUSPENDED")
    void inactiveMemberIsBlocked() {
        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_WITHDRAWN)));
        assertCode(() -> service.login(profile, session), MemberErrorCode.ACCOUNT_WITHDRAWN);

        when(memberService.findLoginMemberBySocial("GOOGLE", "g-1")).thenReturn(Optional.of(loginMember(Member.STATUS_SUSPENDED)));
        assertCode(() -> service.login(profile, session), MemberErrorCode.ACCOUNT_SUSPENDED);

        verify(authService, never()).recordLoginSuccess(any());
    }

    @Test
    @DisplayName("처음이고 이메일이 새것이면 가입이 필요하다고 답하고, 정규화한 이메일을 세션에 담는다")
    void newPersonNeedsSignup() {
        when(memberService.findLoginMemberBySocial(anyString(), anyString())).thenReturn(Optional.empty());
        when(memberService.isEmailAvailable("Minji@Gmail.com")).thenReturn(AvailabilityDTO.of(true));

        SocialLoginService.Result result = service.login(profile, session);

        assertThat(result.signupRequired()).isTrue();
        SocialProfile pending = service.getPending(session);
        assertThat(pending.email()).isEqualTo("minji@gmail.com");
        assertThat(pending.providerUserId()).isEqualTo("g-1");
        verify(authService, never()).recordLoginSuccess(any());
    }

    @Test
    @DisplayName("처음인데 같은 이메일로 이미 가입한 사람이 있으면 자동으로 연결하지 않고 EMAIL_DUPLICATED")
    void sameEmailIsNotAutoLinked() {
        when(memberService.findLoginMemberBySocial(anyString(), anyString())).thenReturn(Optional.empty());
        when(memberService.isEmailAvailable(anyString())).thenReturn(AvailabilityDTO.of(false));

        assertCode(() -> service.login(profile, session), MemberErrorCode.EMAIL_DUPLICATED);
        assertThat(session.getAttribute(SocialLoginService.SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("제공자가 준 이메일 형식이 올바르지 않으면 OAUTH_FAILED")
    void invalidEmail() {
        when(memberService.findLoginMemberBySocial(anyString(), anyString())).thenReturn(Optional.empty());

        assertCode(() -> service.login(new SocialProfile("GOOGLE", "g-1", "not-an-email", null), session), MemberErrorCode.OAUTH_FAILED);
        verify(memberService, never()).isEmailAvailable(anyString());
    }

    @Test
    @DisplayName("가입을 이어서 하는 정보가 세션에 없으면 OAUTH_SESSION_EXPIRED (세션 자체가 없을 때도 같다)")
    void pendingMissing() {
        assertCode(() -> service.getPending(session), MemberErrorCode.OAUTH_SESSION_EXPIRED);
        assertCode(() -> service.getPending(null), MemberErrorCode.OAUTH_SESSION_EXPIRED);
        assertCode(() -> service.completeSignup(new SocialSignupDTO(), session), MemberErrorCode.OAUTH_SESSION_EXPIRED);
        verify(memberSignupService, never()).signupSocial(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("가입을 마치면 세션의 이메일·제공자 정보로 가입하고, 세션 정보를 지운 뒤 로그인 기록을 남긴다")
    void completeSignup() {
        session.setAttribute(SocialLoginService.SESSION_KEY, new SocialProfile("KAKAO", "k-9", "minji@kakao.com", "민지"));
        SocialSignupDTO request = new SocialSignupDTO("민지", LocalDate.of(1999, 4, 12), "FEMALE", List.of(1L));
        when(memberSignupService.signupSocial(request, "minji@kakao.com", "KAKAO", "k-9")).thenReturn(loginMember(Member.STATUS_ACTIVE));

        LoginMemberDTO member = service.completeSignup(request, session);

        assertThat(member.getMemberId()).isEqualTo(7L);
        assertThat(session.getAttribute(SocialLoginService.SESSION_KEY)).isNull();
        verify(authService).recordLoginSuccess(7L);
    }

    @Test
    @DisplayName("가입이 거절되면(닉네임 중복 등) 세션 정보를 지우지 않아 고쳐서 다시 보낼 수 있다")
    void failedSignupKeepsPending() {
        SocialProfile pending = new SocialProfile("GOOGLE", "g-1", "minji@gmail.com", "민지");
        session.setAttribute(SocialLoginService.SESSION_KEY, pending);
        SocialSignupDTO request = new SocialSignupDTO("민지", LocalDate.of(1999, 4, 12), null, List.of(1L));
        when(memberSignupService.signupSocial(any(), anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED));

        assertCode(() -> service.completeSignup(request, session), MemberErrorCode.NICKNAME_DUPLICATED);

        assertThat(service.getPending(session)).isEqualTo(pending);
        verify(authService, never()).recordLoginSuccess(any());
    }
}
