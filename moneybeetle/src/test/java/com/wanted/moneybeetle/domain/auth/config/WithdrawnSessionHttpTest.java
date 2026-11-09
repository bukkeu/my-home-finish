package com.wanted.moneybeetle.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.time.LocalDateTime;
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
*   탈퇴·정지된 계정의 다른 세션이 막히는지 실제 HTTP 요청으로 확인한다 (이슈 #8). Security 필터 체인(formLogin·CSRF·세션·로그아웃)과 핸들러를 모두 띄우고,
*   회원 저장소만 가짜로 둔다 (DB 없이 돈다). 로그인 요청이 폼 형식(email, password)이라는 것과 CSRF 토큰이 필요하다는 것도 여기서 고정한다.
*  */
@SpringBootTest(classes = {WithdrawnSessionHttpTest.TestApp.class, SecurityConfig.class, SecurityErrorHandler.class,
        AuthSuccessHandler.class, com.wanted.moneybeetle.domain.auth.handler.AdminAuthSuccessHandler.class,
        AuthFailHandler.class, AuthLogoutSuccessHandler.class, AuthService.class,
        com.wanted.moneybeetle.domain.auth.model.service.LoginAuthenticationProvider.class,
        MemberService.class, com.wanted.moneybeetle.domain.member.model.service.AccountService.class,
        com.wanted.moneybeetle.domain.member.controller.MemberController.class,
        WithdrawnSessionHttpTest.ProtectedController.class})
class WithdrawnSessionHttpTest {

    private static final String EMAIL = "minji@gmail.com";
    private static final String PASSWORD = "beetle1234";

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
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

    @MockitoBean
    private MemberRepository memberRepository;
    @MockitoBean
    private InstructorProfileRepository instructorProfileRepository;
    @MockitoBean
    private TermsRepository termsRepository;
    @MockitoBean
    private com.wanted.moneybeetle.domain.member.model.dao.PasswordResetTokenRepository passwordResetTokenRepository;
    @MockitoBean
    private com.wanted.moneybeetle.domain.member.model.service.MemberProfileService memberProfileService;
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
    @DisplayName("두 곳에서 로그인한 계정이 탈퇴하면 탈퇴 요청을 보내지 않은 다른 세션의 다음 요청도 401 로 거부된다")
    void otherSessionIsRejectedAfterWithdrawal() throws Exception {
        MockHttpSession phone = new MockHttpSession();
        MockHttpSession laptop = new MockHttpSession();
        loginForm(EMAIL, PASSWORD, phone).andExpect(status().isOk());
        loginForm(EMAIL, PASSWORD, laptop).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses").session(phone)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses").session(laptop)).andExpect(status().isOk());

        // phone 세션에서 실제 탈퇴 API 로 탈퇴한다 (MemberController → AccountService.withdraw)
        mockMvc.perform(delete("/api/v1/members/me").with(csrf()).session(phone)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
        assertThat(phone.isInvalid()).isTrue();   // 탈퇴한 세션 자신은 컨트롤러가 끝낸다

        // 탈퇴 요청을 보내지 않은 laptop 세션은 ActiveMemberFilter 가 막는다
        mockMvc.perform(get("/api/v1/courses").session(laptop))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertThat(laptop.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("정지된 계정의 이미 열린 세션도 다음 요청에서 거부된다")
    void suspendedAccountSessionIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        loginForm(EMAIL, PASSWORD, session).andExpect(status().isOk());

        ReflectionTestUtils.setField(member, "status", Member.STATUS_SUSPENDED);

        mockMvc.perform(get("/api/v1/courses").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 계정의 세션은 계속 쓸 수 있다")
    void activeSessionKeepsWorking() throws Exception {
        MockHttpSession session = new MockHttpSession();
        loginForm(EMAIL, PASSWORD, session).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses").session(session)).andExpect(status().isOk());
    }
}
