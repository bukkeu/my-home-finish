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
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
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
    private SocialLoginService service;

    private final SocialProfile profile = new SocialProfile("GOOGLE", "g-1", "Minji@Gmail.com", "민지");

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(memberService, memberSignupService, authService, clientRegistrations);
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
    @org.junit.jupiter.api.DisplayName("지원하지 않는 제공자·등록 정보가 아예 없으면 OAUTH_PROVIDER_UNAVAILABLE")
    void unavailableProviders() {
        givenGoogleRegistered();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireAvailableProvider("naver"))   // 지원하지 않는 제공자
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.OAUTH_PROVIDER_UNAVAILABLE));

        org.mockito.Mockito.when(clientRegistrations.getIfAvailable()).thenReturn(null);   // 키가 하나도 없어 등록 정보가 없다
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireAvailableProvider("google"))
                .isInstanceOf(BusinessException.class);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("isProviderAvailable: 등록된 제공자만 true 이고, 등록 안 된·지원하지 않는·null 제공자와 등록 정보가 없을 때는 false 다 (화면 버튼 기준, 예외 없음)")
    void providerAvailabilityForPages() {
        givenGoogleRegistered();

        org.assertj.core.api.Assertions.assertThat(service.isProviderAvailable("google")).isTrue();
        org.assertj.core.api.Assertions.assertThat(service.isProviderAvailable("naver")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.isProviderAvailable(null)).isFalse();

        org.mockito.Mockito.when(clientRegistrations.getIfAvailable()).thenReturn(null);
        org.assertj.core.api.Assertions.assertThat(service.isProviderAvailable("google")).isFalse();
    }

    private static LoginMemberDTO loginMember(String status) {
        Member member = Member.signupSocial("minji@gmail.com", "민지", LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.valueOf(status));
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
        session.setAttribute(SocialLoginService.SESSION_KEY, new SocialProfile("GOOGLE", "g-9", "minji@gmail.com", "민지"));
        SocialSignupDTO request = new SocialSignupDTO("민지", LocalDate.of(1999, 4, 12), "FEMALE", List.of(1L));
        when(memberSignupService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-9")).thenReturn(loginMember(Member.STATUS_ACTIVE));

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
