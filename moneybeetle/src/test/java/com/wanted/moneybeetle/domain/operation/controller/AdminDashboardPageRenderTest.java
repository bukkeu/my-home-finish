package com.wanted.moneybeetle.domain.operation.controller;

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
*   담당 : DBA ② (관리자 운영 화면, 이슈 #61)
*   관리자 대시보드 틀이 실제 SecurityConfig·공통 레이아웃 조각과 함께 그려지는지 확인한다 (DB 없이)
*   - 관리자만 들어온다 (로그인 안 하면 로그인으로, 학습자는 막힘)
*   - ?view= 에 따라 탭 내용과 그 탭의 스크립트만 들어가고, 모르는 값은 대시보드로 보인다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, AdminDashboardViewController.class})
@MockSecurityServices
class AdminDashboardPageRenderTest {

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
    @DisplayName("로그인하지 않으면 화면에 들어오지 못한다")
    void anonymous() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("학습자는 관리자 화면에 들어오지 못한다")
    void member() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user(login(8L, "MEMBER")))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("view 가 없으면 대시보드가 그려지고 대시보드 스크립트만 불러온다")
    void dashboardByDefault() throws Exception {
        String html = render("/admin/dashboard");

        assertThat(html).contains("운영 대시보드", "id=\"adm-stats\"", "id=\"adm-todos\"", "id=\"adm-recent-logs\"")
                .contains("/css/admin.css", "/js/admin-common.js", "/js/admin-overview.js")
                .doesNotContain("/js/admin-terms.js", "/js/admin-growth.js", "/js/admin-settings.js");
    }

    @Test
    @DisplayName("모르는 view 값은 대시보드로 보인다 (주소에 쓴 값이 템플릿을 고르지 못한다)")
    void unknownViewFallsBack() throws Exception {
        String html = render("/admin/dashboard?view=../../secret");

        assertThat(html).contains("운영 대시보드", "/js/admin-overview.js").doesNotContain("secret");
    }

    @Test
    @DisplayName("terms 탭: 날짜별 오늘의 용어와 용어 관리가 그려지고 그 스크립트만 붙는다")
    void termsTab() throws Exception {
        String html = render("/admin/dashboard?view=terms");

        assertThat(html).contains("오늘의 경제 용어", "id=\"adm-days\"", "id=\"adm-editor\"", "id=\"adm-clear-day\"", "자동 선정으로", "id=\"adm-term-save\"", "id=\"adm-terms\"")
                .contains("/js/admin-terms.js")
                .doesNotContain("/js/admin-overview.js", "/js/admin-growth.js");
    }

    @Test
    @DisplayName("growth 탭: 보상 규칙과 캐시 수동 조정이 그려진다")
    void growthTab() throws Exception {
        String html = render("/admin/dashboard?view=growth");

        assertThat(html).contains("id=\"adm-rules\"", "id=\"adm-member-keyword\"", "id=\"adm-adj-amount\"", "id=\"adm-adj-reason\"", "id=\"adm-adj-submit\"")
                .contains("/js/admin-growth.js")
                .doesNotContain("/js/admin-overview.js");
    }

    @Test
    @DisplayName("settings 탭: 서비스 설정과 작업 기록이 그려진다")
    void settingsTab() throws Exception {
        String html = render("/admin/dashboard?view=settings");

        assertThat(html).contains("id=\"adm-settings\"", "id=\"adm-logs\"", "id=\"adm-log-more\"")
                .contains("/js/admin-settings.js")
                .doesNotContain("/js/admin-overview.js");
    }

    @Test
    @DisplayName("다른 담당의 탭(courses, members, board)도 틀 안에서 오류 없이 열리고 대시보드 스크립트는 붙지 않는다")
    void otherOwnersTabsOpen() throws Exception {
        for (String view : List.of("courses", "members", "board")) {
            String html = render("/admin/dashboard?view=" + view);

            assertThat(html).contains("id=\"adm-main\"").doesNotContain("/js/admin-overview.js");
        }
    }

    @Test
    @DisplayName("관리자 메뉴에서 지금 보는 탭이 표시된다")
    void activeMenu() throws Exception {
        assertThat(render("/admin/dashboard?view=terms")).containsPattern("is-active[^>]*>\\s*홈 콘텐츠");
    }
}
