package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.support.SecurityTestApp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인·로그아웃을 실제 HTTP 요청으로 확인한다 (이슈 #6). Security 필터 체인(formLogin·CSRF·세션·로그아웃)과 핸들러를 모두 띄우고,
*   회원 저장소만 가짜로 둔다 (DB 없이 돈다). 로그인 요청이 폼 형식(email, password)이라는 것과 CSRF 토큰이 필요하다는 것도 여기서 고정한다.
*  */
@SpringBootTest(classes = {SecurityTestApp.class, LoginHttpTest.TestApp.class,
        AuthService.class,
        com.wanted.moneybeetle.domain.auth.model.service.LoginAuthenticationProvider.class,
        MemberService.class,
        LoginHttpTest.ProtectedController.class})
class LoginHttpTest {

    private static final String EMAIL = "minji@gmail.com";
    private static final String PASSWORD = "beetle1234";

    // SecurityConfig·로그인 핸들러·DB 제외 설정은 SecurityTestApp(#161). 여기는 이 테스트에만 필요한 빈
    @Configuration
    static class TestApp {
        @Bean
        org.springframework.transaction.PlatformTransactionManager transactionManager() {
            // DB 없이 도는 테스트라 아무것도 하지 않는 트랜잭션 관리자를 쓴다 (로그인이 트랜잭션으로 감싸지는 것만 필요하다)
            var manager = org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
            org.mockito.Mockito.when(manager.getTransaction(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));
            return manager;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    /** 로그인해야 열리는 가짜 주소 */
    @RestController
    static class ProtectedController {
        @GetMapping("/api/v1/courses")
        String courses() {
            return "ok";
        }
    }

    /** 로그인 성공 처리(AuthSuccessHandler)가 출석을 기록할 때 쓴다. 이 테스트는 출석 자체를 보지 않아 가짜로 둔다 */
    @MockitoBean
    private com.wanted.moneybeetle.domain.growth.model.service.AttendanceService attendanceService;
    @MockitoBean
    private MemberRepository memberRepository;
    @MockitoBean
    private InstructorProfileRepository instructorProfileRepository;
    /** 간편 로그인(#9): MemberService 가 쓰는 연결 저장소, SecurityConfig 의 OAuth 성공 처리 핸들러가 쓰는 서비스. 이 테스트는 이메일 로그인만 본다 */
    @MockitoBean
    private com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository socialAccountRepository;
    @MockitoBean
    private com.wanted.moneybeetle.domain.auth.model.service.SocialLoginService socialLoginService;
    @MockitoBean
    private TermsRepository termsRepository;
    @MockitoBean
    private com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository memberTermsAgreementRepository;
    @MockitoBean
    private com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService forbiddenWordService;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PasswordEncoder passwordEncoder;
    private MockMvc mockMvc;
    private Member member;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        member = Member.signup(EMAIL, passwordEncoder.encode(PASSWORD), "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 8L);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(member));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member));
    }

    private org.springframework.test.web.servlet.ResultActions loginForm(String email, String password, MockHttpSession session)
            throws Exception {
        var request = post("/api/v1/auth/login").with(csrf())
                .param("email", email).param("password", password)
                .accept(MediaType.APPLICATION_JSON);
        if (session != null) {
            request.session(session);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("폼 형식(email, password)으로 로그인하면 200 JSON 이고 그 세션으로 로그인 필요한 API 가 열린다")
    void loginWithFormOpensSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        loginForm(EMAIL, PASSWORD, session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(8))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.redirectUrl").value("/user/home"));

        mockMvc.perform(get("/api/v1/courses").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses")).andExpect(status().isUnauthorized());   // 세션이 없으면 401
    }

    @Test
    @DisplayName("CSRF 토큰 없이 보내면 거절된다 (로그인 화면의 폼·fetch 가 토큰을 함께 보내야 한다)")
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("JSON 본문으로 보내면 email·password 를 읽지 못해 401 LOGIN_FAILED (요청은 폼 형식만 받는다)")
    void jsonBodyIsNotSupported() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("틀린 비밀번호는 401, 5번째 시도는 423 LOGIN_LOCKED 이고 잠금 중에는 맞는 비밀번호도 423")
    void lockAfterFiveWrongPasswords() throws Exception {
        for (int i = 1; i <= 4; i++) {
            loginForm(EMAIL, "wrong-password1", null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
        }
        loginForm(EMAIL, "wrong-password1", null)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("LOGIN_LOCKED"));
        loginForm(EMAIL, PASSWORD, null)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("LOGIN_LOCKED"));
        assertThat(member.getFailedLoginCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("로그아웃: fetch(Accept: JSON)는 204, 세션이 끝나 이후 API 는 401")
    void logoutWithFetchReturns204() throws Exception {
        MockHttpSession session = new MockHttpSession();
        loginForm(EMAIL, PASSWORD, session).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/courses").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃: 사이드바 폼처럼 브라우저가 직접 보낸 요청은 첫 화면으로 이동한다")
    void logoutWithFormRedirects() throws Exception {
        MockHttpSession session = new MockHttpSession();
        loginForm(EMAIL, PASSWORD, session).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    /* 로그인 상태 유지 (#367) */

    private MvcResult loginRememberMe() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .param("email", EMAIL).param("password", PASSWORD).param("remember-me", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** 응답의 Set-Cookie 중 remember-me 줄 (없으면 null) */
    private static String rememberMeHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("remember-me="))
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("remember-me=true 로 로그인하면 14일짜리 HttpOnly·SameSite=Lax 쿠키를 주고, 세션 없이 그 쿠키만으로 로그인된다")
    void rememberMeCookieLogsInWithoutSession() throws Exception {
        MvcResult login = loginRememberMe();

        String header = rememberMeHeader(login);
        assertThat(header).isNotNull()
                .contains("Max-Age=1209600")      // 14일
                .contains("HttpOnly");
        // 가짜 응답은 SameSite 를 Set-Cookie 줄에 쓰지 않아 쿠키 속성으로 확인한다 (실제 서버(Tomcat)는 줄에 쓴다)
        Cookie cookie = login.getResponse().getCookie("remember-me");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");

        // 브라우저를 다시 켠 것처럼 세션 없이 쿠키만 보낸다
        mockMvc.perform(get("/api/v1/courses").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("remember-me 를 보내지 않으면 쿠키를 주지 않는다 (지금처럼 세션 30분만)")
    void noRememberMeCookieByDefault() throws Exception {
        MvcResult login = loginForm(EMAIL, PASSWORD, new MockHttpSession()).andExpect(status().isOk()).andReturn();

        assertThat(rememberMeHeader(login)).isNull();
    }

    @Test
    @DisplayName("로그아웃하면 remember-me 쿠키를 지운다 (Max-Age=0)")
    void logoutDeletesRememberMeCookie() throws Exception {
        MvcResult login = loginRememberMe();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session)
                        .cookie(login.getResponse().getCookie("remember-me")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(rememberMeHeader(logout)).isNotNull().contains("Max-Age=0");
    }

    @Test
    @DisplayName("쿠키를 받은 뒤 정지된 계정은 쿠키가 있어도 로그인되지 않고 쿠키가 지워진다")
    void suspendedMemberCannotUseRememberMeCookie() throws Exception {
        Cookie cookie = loginRememberMe().getResponse().getCookie("remember-me");
        member.suspend("테스트 정지");

        MvcResult result = mockMvc.perform(get("/api/v1/courses").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(rememberMeHeader(result)).isNotNull().contains("Max-Age=0");
    }
}
