package com.wanted.moneybeetle.domain.auth.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.exception.BusinessException;
import ch.qos.logback.classic.Level;
import com.wanted.moneybeetle.support.LogCapture;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   OAuth2SuccessHandler (이슈 #9) - 서버·제공자 없이 가짜 요청·응답으로 돈다
*   - 가입한 사람: 이메일 로그인과 같은 로그인 상태(AuthDetails)가 세션에 저장되고 첫 화면으로 이동한다
*   - 처음 온 사람: 로그인 상태 없이 추가 정보 화면으로 이동한다
*   - 막힌 경우·예상 밖 오류: 로그인 상태 없이 로그인 화면으로 이동하며 오류 코드를 알린다
*  */
class OAuth2SuccessHandlerTest {

    private final SocialLoginService socialLoginService = mock(SocialLoginService.class);
    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        request.setSession(new MockHttpSession());
        handler = new OAuth2SuccessHandler(socialLoginService, new AuthSessionWriter(), attendanceService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static OAuth2AuthenticationToken googleToken() {
        DefaultOAuth2User user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("sub", "g-1", "email", "minji@gmail.com", "email_verified", true, "name", "민지"), "sub");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    private static LoginMemberDTO member(String role) {
        Member member = Member.signupSocial("minji@gmail.com", "민지", LocalDate.of(1999, 4, 12), "FEMALE", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        return LoginMemberDTO.from(member, role);
    }

    private SecurityContext savedContext() {
        return (SecurityContext) request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    @Test
    @DisplayName("이미 가입한 회원이면 AuthDetails 로그인 상태를 세션에 저장하고 첫 화면으로 보내며 출석을 기록한다")
    void existingMember() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any())).thenReturn(new SocialLoginService.Result(member(Member.ROLE_MEMBER)));

        handler.onAuthenticationSuccess(request, response, googleToken());

        assertThat(response.getRedirectedUrl()).isEqualTo("/user/home");
        SecurityContext saved = savedContext();
        assertThat(saved).isNotNull();
        assertThat(saved.getAuthentication().getPrincipal()).isInstanceOf(AuthDetails.class);
        assertThat(((AuthDetails) saved.getAuthentication().getPrincipal()).getMemberId()).isEqualTo(7L);
        assertThat(saved.getAuthentication().getAuthorities()).extracting(Object::toString).containsExactly("MEMBER");
        verify(attendanceService).checkToday(7L);
    }

    @Test
    @DisplayName("강사·관리자는 각자의 첫 화면으로 가고 출석은 기록하지 않는다")
    void instructorAndAdmin() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any())).thenReturn(new SocialLoginService.Result(member(Member.ROLE_INSTRUCTOR)));
        handler.onAuthenticationSuccess(request, response, googleToken());
        assertThat(response.getRedirectedUrl()).isEqualTo("/instructor/dashboard");

        MockHttpServletResponse second = new MockHttpServletResponse();
        when(socialLoginService.login(any(SocialProfile.class), any())).thenReturn(new SocialLoginService.Result(member(Member.ROLE_ADMIN)));
        handler.onAuthenticationSuccess(request, second, googleToken());
        assertThat(second.getRedirectedUrl()).isEqualTo("/admin/dashboard");

        verify(attendanceService, never()).checkToday(any());
    }

    @Test
    @DisplayName("출석 기록이 실패해도 로그인은 성공한다")
    void attendanceFailureDoesNotBlockLogin() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any())).thenReturn(new SocialLoginService.Result(member(Member.ROLE_MEMBER)));
        when(attendanceService.checkToday(7L)).thenThrow(new IllegalStateException("db down"));

        handler.onAuthenticationSuccess(request, response, googleToken());

        assertThat(response.getRedirectedUrl()).isEqualTo("/user/home");
        assertThat(savedContext()).isNotNull();
    }

    @Test
    @DisplayName("처음 온 사람은 로그인 상태 없이 추가 정보 화면으로 간다")
    void firstTimeUser() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any())).thenReturn(new SocialLoginService.Result(null));
        // 제공자 인증을 막 마친 직후라 Security 가 OAuth2 인증 정보를 세션에 저장해 둔 상태를 흉내 낸다
        SecurityContext oauthContext = SecurityContextHolder.createEmptyContext();
        oauthContext.setAuthentication(googleToken());
        request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, oauthContext);

        handler.onAuthenticationSuccess(request, response, googleToken());

        assertThat(response.getRedirectedUrl()).isEqualTo("/signup/social");
        assertThat(savedContext()).isNull();   // 가입하기 전이라 로그인 상태가 남지 않는다
    }

    @Test
    @DisplayName("막힌 경우(이미 가입된 이메일 등)는 로그인 화면으로 가며 오류 코드만 알린다")
    void blocked() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any())).thenThrow(new BusinessException(MemberErrorCode.EMAIL_DUPLICATED));

        handler.onAuthenticationSuccess(request, response, googleToken());

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=EMAIL_DUPLICATED");
        assertThat(savedContext()).isNull();
    }

    @Test
    @DisplayName("제공자 정보가 부족하면(이메일 미확인 등) OAUTH_FAILED, 서비스는 부르지 않는다")
    void unverifiedEmail() throws IOException {
        DefaultOAuth2User user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("sub", "g-1", "email", "minji@gmail.com", "email_verified", false), "sub");

        handler.onAuthenticationSuccess(request, response, new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=OAUTH_FAILED");
        verify(socialLoginService, never()).login(any(), any());
    }

    @Test
    @DisplayName("예상 밖 오류도 OAUTH_FAILED 로 로그인 화면에 보내고 로그인 상태를 남기지 않는다")
    void unexpectedError() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any())).thenThrow(new IllegalStateException("db down"));

        handler.onAuthenticationSuccess(request, response, googleToken());

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=OAUTH_FAILED");
        assertThat(savedContext()).isNull();
    }

    @Test
    @DisplayName("예상 밖 오류는 원인을 warn 로그로 남기되(스택 포함), 오류 메시지에 든 이메일 주소는 가린다 (#299)")
    void unexpectedErrorLogsMaskedCause() throws IOException {
        when(socialLoginService.login(any(SocialProfile.class), any()))
                .thenThrow(new IllegalStateException("Duplicate entry 'minji@gmail.com' for key 'uk_member_email'"));

        try (LogCapture logs = LogCapture.of(OAuth2SuccessHandler.class)) {
            handler.onAuthenticationSuccess(request, response, googleToken());

            assertThat(logs.events()).hasSize(1);
            var event = logs.events().get(0);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("간편 로그인 처리에 실패했어요");
            assertThat(event.getThrowableProxy().getMessage()).startsWith("java.lang.IllegalStateException: ")
                    .contains("'***'").doesNotContain("minji", "gmail");
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("OAuth2 인증 정보가 아니면 OAUTH_FAILED")
    void notOauthToken() throws IOException {
        handler.onAuthenticationSuccess(request, response,
                new org.springframework.security.authentication.TestingAuthenticationToken("u", "p"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=OAUTH_FAILED");
    }
}
