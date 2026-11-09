package com.wanted.moneybeetle.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.handler.AuthFailHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthLogoutSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인·로그아웃을 실제 HTTP 요청으로 확인한다 (이슈 #6). Security 필터 체인(formLogin·CSRF·세션·로그아웃)과 핸들러를 모두 띄우고,
*   회원 저장소만 가짜로 둔다 (DB 없이 돈다). 로그인 요청이 폼 형식(email, password)이라는 것과 CSRF 토큰이 필요하다는 것도 여기서 고정한다.
*  */
@SpringBootTest(classes = {LoginHttpTest.TestApp.class, SecurityConfig.class, SecurityErrorHandler.class,
        AuthSuccessHandler.class, AuthFailHandler.class, AuthLogoutSuccessHandler.class, AuthService.class,
        MemberService.class,
        LoginHttpTest.ProtectedController.class})
class LoginHttpTest {

    private static final String EMAIL = "minji@gmail.com";
    private static final String PASSWORD = "beetle1234";

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
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

    @MockitoBean
    private MemberRepository memberRepository;
    @MockitoBean
    private InstructorProfileRepository instructorProfileRepository;
    @MockitoBean
    private TermsRepository termsRepository;

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
}
