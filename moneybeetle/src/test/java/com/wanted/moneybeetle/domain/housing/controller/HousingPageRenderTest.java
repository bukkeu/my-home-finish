package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.model.dto.HousingPageDTO;
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
*   #58 모의 청약 화면이 실제 SecurityConfig·공통 레이아웃 조각과 함께 그려지는지 확인한다 (DB 없이)
*   - 로그인 안 하면 화면에 들어오지 못한다
*   - Lv.12 미만 : 잠금 안내 + 레벨이 필요한 영역(모집 중 단지·캘린더) 흐리게 (SP-24, NFR-09)
*   - Lv.12 이상 : 잠금 안내·흐림 없음
*   - 화면 스크립트(housing.js)·스타일(housing.css)과 data-* 값(잠금·오늘)이 들어간다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, HousingViewController.class})
@MockSecurityServices
class HousingPageRenderTest {

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
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    private String render(int level) throws Exception {
        when(housingPageService.getPage(8L)).thenReturn(new HousingPageDTO(level, 12, level < 12, TODAY));
        return mockMvc.perform(get("/user/demo-housing-subscription").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("housing/housing"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 로그인하지_않으면_화면에_들어오지_못한다() throws Exception {
        mockMvc.perform(get("/user/demo-housing-subscription"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void Lv12_미만이면_잠금_안내와_흐림이_보인다() throws Exception {
        String html = render(8);

        assertThat(html).contains("모의 청약은 Lv.12부터 이용할 수 있어요.")
                .contains("경험치를 모아 Lv.12 달성 (현재 Lv.8)")
                .contains("mb-blur")
                .contains("Lv.12부터 볼 수 있어요")
                .contains("청약 단지는 Lv.12부터 볼 수 있어요.")
                .doesNotContain("id=\"hs-score-form\"")
                .contains("data-locked=\"true\"");
    }

    @Test
    void Lv12_이상이면_잠금_안내와_흐림이_없다() throws Exception {
        String html = render(12);

        assertThat(html).doesNotContain("모의 청약은 Lv.12부터 이용할 수 있어요.")
                .doesNotContain("mb-blur")
                .contains("data-locked=\"false\"")
                .contains("id=\"hs-score-form\"")
                .contains("가점 계산하고 신청하기")
                .doesNotContain("청약 단지는 Lv.12부터 볼 수 있어요.")
                .contains("이용 가능");
    }

    @Test
    void 화면_스크립트와_스타일_그리고_오늘_날짜가_들어간다() throws Exception {
        String html = render(12);

        assertThat(html).contains("/js/housing.js")
                .contains("/css/housing.css")
                .contains("/js/common.js")
                .contains("data-today=\"2026-11-10\"")
                .contains("청약 홈").contains("청약 단지").contains("내 신청 내역")
                .contains("가상 실습이며 실제 금융 거래가 아니에요");
    }
}
