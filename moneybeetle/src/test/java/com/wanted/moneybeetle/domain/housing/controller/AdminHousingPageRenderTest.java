package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.model.service.HousingPageService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

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

/* comment.
*   담당 : SCM (모의 청약)
*   #63 관리자 모의 청약 운영 화면이 실제 SecurityConfig·공통 레이아웃 조각과 함께 그려지는지 확인한다 (DB 없이)
*   - 관리자만 들어온다 (로그인 안 하면 로그인으로, 학습자는 막힘)
*   - 오늘 날짜(data-today)와 화면 스크립트·스타일, 공고 폼·탭이 들어간다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, AdminHousingViewController.class})
@MockSecurityServices
class AdminHousingPageRenderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @MockitoBean
    private HousingPageService housingPageService;
    @Autowired
    private MemberService memberService;
    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        when(housingPageService.today()).thenReturn(TODAY);
        when(housingPageService.unlockLevel()).thenReturn(10);   // 운영 설정으로 바꾼 해금 레벨 (#223)
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private AuthDetails login(long memberId, String authority) {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(memberId);
        when(details.getUsername()).thenReturn(authority.toLowerCase() + "@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority(authority))).when(details).getAuthorities();
        return details;
    }

    @Test
    void 로그인하지_않으면_화면에_들어오지_못한다() throws Exception {
        mockMvc.perform(get("/admin/housing-management"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void 학습자는_관리자_화면에_들어오지_못한다() throws Exception {
        mockMvc.perform(get("/admin/housing-management").with(user(login(8L, "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_운영_화면을_본다() throws Exception {
        String html = mockMvc.perform(get("/admin/housing-management").with(user(login(1L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/housing"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-today=\"2026-11-10\"")
                .contains("/js/admin-housing.js")
                .contains("/css/admin-housing.css")
                .contains("/js/common.js")
                .contains("id=\"ah-form\"")
                // 시안(AdminHousingManagement)의 왼쪽 모의 청약 관리 메뉴 4개와 돌아가기 링크
                .contains("모의 청약 관리").contains("대시보드").contains("청약 공고 관리")
                .contains("신청 현황").contains("당첨 결과 관리").contains("관리자 대시보드로")
                .contains("data-mb-drawer")                   // 모바일에서 공통 햄버거로 열린다
                .contains("Lv.10 이상")                       // 신청 조건 안내는 운영 설정 해금 레벨 (#223)
                .contains("가상 실습이며 실제 금융 거래가 아니에요");   // NFR-12 고지 (공통 조각)
    }
}
