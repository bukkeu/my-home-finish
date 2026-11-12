package com.wanted.moneybeetle.domain.auth.config;

import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest(classes = {SecurityTestApp.class, SecurityConfigTest.DummyController.class})
@MockSecurityServices        // 로그인 핸들러·계정 상태 확인이 쓰는 서비스는 가짜. 이 테스트는 URL 권한만 본다 (#161)
class SecurityConfigTest {

    // 권한 규칙만 보기 위한 가짜 주소들
    @RestController
    static class DummyController {
        @GetMapping({"/api/v1/auth/check-email", "/api/v1/admin/auth/login", "/api/v1/courses",
                "/api/v1/admin/members", "/api/v1/instructor/courses", "/api/v1/instructor/profile",
                "/api/v1/instructor/quizzes", "/api/v1/instructor/summaries", "/admin/dashboard", "/mypage",
                "/instructor/dashboard"})
        String ok() {
            return "ok";
        }
    }

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
    void 화면에_쓰는_정적_파일은_로그인_없이_열린다() throws Exception {
        // 열려 있지 않으면 로그인 전 화면(/login)의 CSS·JS·로고가 /login 으로 리다이렉트돼 디자인이 나오지 않는다.
        // 파일이 있으면 200, 이 테스트 앱에 파일이 없으면 404 만 허용한다. 그 밖의 상태(로그인 화면 리다이렉트 3xx, 401, 403 등)는 모두 실패
        for (String path : new String[] {"/css/auth.css", "/js/auth.js", "/images/logo.png"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(status).as(path).isIn(200, 404);
        }
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
    void 일반_회원이_강사_센터_퀴즈_요약글_API를_부르면_403() throws Exception {   // #366
        mockMvc.perform(get("/api/v1/instructor/quizzes").with(as("MEMBER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/instructor/summaries").with(as("MEMBER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/instructor/quizzes").with(as("INSTRUCTOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/instructor/summaries").with(as("INSTRUCTOR"))).andExpect(status().isOk());
    }

    @Test
    void 학생_계정이_강사_프로필_API를_부르면_403_JSON() throws Exception {   // 이슈 #365, API 12-1·12-2
        mockMvc.perform(get("/api/v1/instructor/profile").with(as("MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/instructor/profile"))
                .andExpect(status().isUnauthorized());
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
        mockMvc.perform(get("/api/v1/instructor/profile").with(as("INSTRUCTOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses").with(as("MEMBER"))).andExpect(status().isOk());
    }
}
