package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.config.HomeWebConfig;
import com.wanted.moneybeetle.domain.home.config.LayoutInterceptor;
import com.wanted.moneybeetle.domain.home.model.dto.LayoutDTO;
import com.wanted.moneybeetle.domain.home.model.service.LayoutService;
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
*   담당 : SCM (#54, 마이페이지 틀)
*   마이페이지 틀이 실제 SecurityConfig·공통 레이아웃 조각과 함께 그려지는지 확인한다 (DB 없이)
*   - 로그인 회원만 들어온다
*   - 탭 7개(합의한 탭 이름)와 탭 영역이 있고, 아직 없는 담당 조각은 "준비 중" 자리로 대신 그려진다 (화면이 깨지지 않는다)
*   - 화면 스크립트·스타일이 들어간다
*   - 프로필 카드 경험치는 공통 레이아웃 값(LayoutInterceptor, #249)의 지금 레벨 경험치·레벨 구간을 쓴다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, MyPageViewController.class, HomeWebConfig.class, LayoutInterceptor.class})
@MockSecurityServices
class MyPageRenderTest {

    private static final List<String> TABS = List.of("settings", "growth", "housing", "wrong", "scrap", "learning", "qna");
    private static final List<String> LEARNING_TABS = List.of("wrong", "scrap", "learning", "qna");   // #71 조각이 있는 탭

    @Autowired
    private MemberService memberService;
    @MockitoBean
    private LayoutService layoutService;
    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        // 민지 Lv.8 투룸 : 지금 레벨에서 90 XP, 레벨 구간 150 XP
        when(layoutService.getLayout(8L)).thenReturn(new LayoutDTO("민지", 8, "투룸", 12000L, 90, 150, 90000L, 2,
                null, 8, 12));
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    private String render() throws Exception {
        return mockMvc.perform(get("/user/my-page").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("member/mypage"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 로그인하지_않으면_들어오지_못한다() throws Exception {
        mockMvc.perform(get("/user/my-page")).andExpect(status().is3xxRedirection());
    }

    @Test
    void 탭_7개와_탭_영역이_합의한_이름으로_있다() throws Exception {
        String html = render();

        for (String tab : TABS) {
            assertThat(html).contains("data-tab=\"" + tab + "\"").contains("data-panel=\"" + tab + "\"")
                    // 탭과 패널이 서로를 가리킨다 (화면 낭독기)
                    .contains("id=\"mp-tab-" + tab + "\" aria-controls=\"mp-panel-" + tab + "\"")
                    .contains("id=\"mp-panel-" + tab + "\" aria-labelledby=\"mp-tab-" + tab + "\"");
        }
        assertThat(html).contains("계정 설정").contains("캐시 내역").contains("나의 청약통장").contains("퀴즈 오답노트")
                .contains("강의 스크랩").contains("학습 기록").contains("Q&amp;A·문의 모음");
    }

    @Test
    void 아직_없는_탭_조각은_준비_중_자리로_대신_그린다() throws Exception {
        String html = render();

        // 조각 파일이 있지만 조각 이름이 아직 없는 경우(settings·growth)와 파일 자체가 없는 경우(housing) 모두.
        // 학습 탭 4개(wrong·scrap·learning·qna)는 #71 에서 조각을 만들어 "준비 중" 자리가 아니다
        assertThat(html.split("이 탭은 준비 중이에요.", -1).length - 1).isEqualTo(TABS.size() - LEARNING_TABS.size());
    }

    @Test
    void 학습_탭_4개는_신아림_조각을_그린다() throws Exception {
        String html = render();

        // #71 fragments/mypage/learning.html : 탭마다 data-ml 영역, 스크립트·스타일은 한 번만
        for (String tab : LEARNING_TABS) {
            assertThat(html).contains("data-ml=\"" + tab + "\"");
        }
        assertThat(html.split("/js/mypage-learning.js", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("/css/mypage-learning.css");
    }

    @Test
    void 프로필_카드_경험치는_공통_레이아웃_값을_쓴다() throws Exception {
        String html = render();

        assertThat(html).contains("data-exp-in=\"90\"").contains("data-exp-per=\"150\"")
                .contains("12,000");                              // 헤더 캐시도 같은 레이아웃 값
    }

    @Test
    void 화면_스크립트와_스타일이_들어간다() throws Exception {
        assertThat(render()).contains("/js/mypage.js").contains("/css/mypage.css").contains("/js/common.js")
                .contains("id=\"mp\"");
    }
}
