package com.wanted.moneybeetle.domain.course.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.controller.AdminDashboardViewController;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/* comment.
*   담당 : DBA ① 신아림 (#253, 관리자 강의 운영·회원·강사·문의 게시판 탭)
*   관리자 대시보드 틀(#61) 안에 신아림 담당 탭 조각과 그 탭의 스크립트만 들어가는지 확인한다 (DB 없이)
*   - courses : fragments/admin/courses (data-ao-courses) + admin-courses.js
*   - members : fragments/admin/members 의 회원·강사 부분만 + admin-members.js
*   - board   : 같은 파일의 문의 게시판 부분만 + admin-members.js
*  */
@SpringBootTest(classes = {SecurityTestApp.class, AdminDashboardViewController.class})
@MockSecurityServices
class AdminOpsTabsRenderTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private AuthDetails login(long memberId, String authority) {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(memberId);
        when(details.getUsername()).thenReturn(authority.toLowerCase() + "@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority(authority))).when(details).getAuthorities();
        return details;
    }

    private String render(String url) throws Exception {
        return mockMvc.perform(get(url).with(user(login(1L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("강의 운영 탭은 검수·전체 강의·카테고리 영역과 admin-courses.js 를 그린다")
    void coursesTab() throws Exception {
        String html = render("/admin/dashboard?view=courses");

        assertThat(html).contains("data-ao-courses", "role=\"tablist\"", "aria-controls=\"ao-pane-review\"", "data-ao-pane=\"review\"", "data-ao-pane=\"all\"", "data-ao-pane=\"categories\"",
                        "/css/admin-ops.css", "/js/admin-common.js", "/js/admin-courses.js")
                .doesNotContain("/js/admin-members.js", "/js/admin-overview.js");
    }

    @Test
    @DisplayName("회원·강사 탭은 회원 부분만 그리고 문의 게시판 부분은 넣지 않는다")
    void membersTab() throws Exception {
        String html = render("/admin/dashboard?view=members");

        assertThat(html).contains("data-ao-members", "data-ao-pane=\"users\"", "data-ao-pane=\"applications\"", "/js/admin-members.js")
                .doesNotContain("data-ao-board", "/js/admin-courses.js");
    }

    @Test
    @DisplayName("문의 게시판 탭은 문의 부분만 그리고 같은 스크립트를 쓴다")
    void boardTab() throws Exception {
        String html = render("/admin/dashboard?view=board");

        assertThat(html).contains("data-ao-board", "data-ao-filter=\"hidden\"", "/js/admin-members.js")
                .doesNotContain("data-ao-members", "/js/admin-courses.js");
    }

    @Test
    @DisplayName("관리자가 아닌 회원·강사는 세 탭 모두 서버에서 막힌다")
    void forbiddenForNonAdmin() throws Exception {
        for (String authority : List.of("MEMBER", "INSTRUCTOR")) {
            for (String view : List.of("courses", "members", "board")) {
                mockMvc.perform(get("/admin/dashboard?view=" + view).with(user(login(2L, authority))))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    @DisplayName("로그인하지 않으면 강의 운영 탭에 들어오지 못하고 로그인으로 보낸다")
    void anonymousRedirected() throws Exception {
        mockMvc.perform(get("/admin/dashboard?view=courses")).andExpect(status().is3xxRedirection());
    }
}
