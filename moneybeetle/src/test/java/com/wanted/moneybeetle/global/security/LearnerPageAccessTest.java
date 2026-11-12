package com.wanted.moneybeetle.global.security;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   학습자 화면(/user/**)은 학습자(MEMBER)만 (#415)
*   - 강사·관리자로 로그인한 채 주소만 /user/... 로 바꾸면 학습자 로그인 화면(/login?next=원래 주소)으로 간다
*   - 학습자는 그대로 / 로그인 안 함은 지금처럼 로그인 화면 / 강사 화면·API 의 기존 응답(403·JSON)은 그대로
*   실제 SecurityConfig 를 띄우고, 화면 대신 글자를 돌려주는 가짜 주소로 확인한다 (DB 없음)
*  */
@SpringBootTest(classes = {SecurityTestApp.class, LearnerPageAccessTest.Pages.class})
@MockSecurityServices
class LearnerPageAccessTest {

    @RestController
    static class Pages {
        @GetMapping("/user/my-page")
        String myPage() {
            return "learner page";
        }

        @GetMapping("/instructor/dashboard")
        String instructorPage() {
            return "instructor page";
        }

        @GetMapping("/api/v1/members/me")
        String me() {
            return "{}";
        }
    }

    @Autowired
    private WebApplicationContext context;
    /** ActiveMemberFilter 가 요청마다 계정 상태를 확인한다 (@MockSecurityServices 의 가짜). 모두 정상 계정이다 */
    @Autowired
    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static AuthDetails login(String role) {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(2L);
        when(details.getUsername()).thenReturn("someone@moneybeetle.local");
        when(details.getRole()).thenReturn(role);
        doReturn(List.of(new SimpleGrantedAuthority(role))).when(details).getAuthorities();
        return details;
    }

    @Test
    @DisplayName("학습자(MEMBER)는 학습자 화면에 그대로 들어간다")
    void memberOpensLearnerPage() throws Exception {
        mockMvc.perform(get("/user/my-page").with(user(login("MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string("learner page"));
    }

    @Test
    @DisplayName("강사로 로그인한 채 주소만 /user/my-page 로 바꾸면 학습자 로그인 화면으로 간다 (돌아갈 주소 next 포함)")
    void instructorIsSentToLearnerLogin() throws Exception {
        mockMvc.perform(get("/user/my-page").with(user(login("INSTRUCTOR"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?next=%2Fuser%2Fmy-page"));
    }

    @Test
    @DisplayName("관리자도 학습자 화면은 학습자 로그인 화면으로 간다. 쿼리(?tab=)도 next 에 남는다")
    void adminIsSentToLearnerLoginWithQuery() throws Exception {
        // MockMvc 의 param() 은 쿼리 문자열을 만들지 않아, 브라우저처럼 주소에 직접 붙인다
        mockMvc.perform(get("/user/my-page?tab=wrong").with(user(login("ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?next=%2Fuser%2Fmy-page%3Ftab%3Dwrong"));
    }

    @Test
    @DisplayName("로그인 안 함은 지금처럼 로그인 화면으로 간다")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get("/user/my-page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("학습자가 강사 화면을 열면 지금처럼 403 (로그인 화면으로 보내지 않는다)")
    void memberOnInstructorPageIsStill403() throws Exception {
        mockMvc.perform(get("/instructor/dashboard").with(user(login("MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("강사는 강사 화면에 그대로 들어가고, 공통 API(/api/v1/members/me)도 지금처럼 쓴다")
    void instructorKeepsOwnPagesAndApi() throws Exception {
        mockMvc.perform(get("/instructor/dashboard").with(user(login("INSTRUCTOR"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/members/me").with(user(login("INSTRUCTOR"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API 의 권한 없음은 지금처럼 403 JSON (로그인 화면으로 보내지 않는다)")
    void apiForbiddenStaysJson() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").with(user(login("INSTRUCTOR"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
