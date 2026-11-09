package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.domain.auth.handler.AuthFailHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthLogoutSuccessHandler;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   SecurityConfig URL 권한 + SecurityErrorHandler 응답 확인 (#4)
*   DB 없이 Security 와 MVC 만 띄우고, 로그인은 가짜 사용자(authority = member.role)로 대신한다
*  */
@SpringBootTest(classes = {SecurityConfigTest.TestApp.class, SecurityConfig.class,
        SecurityErrorHandler.class, AuthSuccessHandler.class, AuthFailHandler.class, AuthLogoutSuccessHandler.class,
        SecurityConfigTest.DummyController.class})
class SecurityConfigTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
    }

    // 권한 규칙만 보기 위한 가짜 주소들
    @RestController
    static class DummyController {
        @GetMapping({"/api/v1/auth/check-email", "/api/v1/admin/auth/login", "/api/v1/courses",
                "/api/v1/admin/members", "/api/v1/instructor/courses", "/admin/dashboard", "/mypage",
                "/instructor/dashboard"})
        String ok() {
            return "ok";
        }
    }

    /** 로그인 핸들러(#6)가 필요로 하는 서비스. 이 테스트는 URL 권한만 보므로 가짜로 둔다 */
    @MockitoBean
    private AuthService authService;
    /** 로그인해 둔 세션의 계정 상태 확인(ActiveMemberFilter)이 쓴다. 이 테스트의 가짜 사용자는 AuthDetails 가 아니라 호출되지 않는다 */
    @MockitoBean
    private com.wanted.moneybeetle.domain.member.model.service.MemberService memberService;
    @MockitoBean
    private com.wanted.moneybeetle.domain.auth.model.service.LoginAuthenticationProvider loginAuthenticationProvider;

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor as(String role) {
        return SecurityMockMvcRequestPostProcessors.user("tester").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
    }

    @Test
    void 비회원도_쓰는_API는_로그인_없이_열린다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-email")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/auth/login")).andExpect(status().isOk());
    }

    @Test
    void 로그인_안_하고_API를_부르면_401_JSON() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 로그인_안_하고_화면에_오면_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(get("/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 일반_회원이_관리자_API를_부르면_403_JSON() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").with(as("MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/instructor/courses").with(as("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 일반_회원이_관리자_화면에_오면_403() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(as("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인_안_하고_관리자_API를_부르면_403이_아니라_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 일반_회원이_강사_화면에_오면_403() throws Exception {
        mockMvc.perform(get("/instructor/dashboard").with(as("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 강사가_관리자_API나_화면에_오면_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").with(as("INSTRUCTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/dashboard").with(as("INSTRUCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 역할이_맞으면_통과한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/instructor/courses").with(as("INSTRUCTOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses").with(as("MEMBER"))).andExpect(status().isOk());
    }
}
