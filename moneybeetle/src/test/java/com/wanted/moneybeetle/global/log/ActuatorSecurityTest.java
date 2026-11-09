package com.wanted.moneybeetle.global.log;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #84 Actuator + 실제 SecurityConfig + application.yaml 설정으로 확인 (DB 는 띄우지 않는다)
*   - /actuator/health 는 로그인 없이 UP, 세부 정보(components)는 보여 주지 않는다
*   - /actuator/info 는 로그인 없이 앱 이름
*   - 그 외 actuator 주소(env, beans)는 웹에 열지 않는다 : 로그인 안 하면 로그인 화면으로, 로그인해도 404
*   - 요청 로그 필터가 함께 돌아 응답에 X-Request-Id 가 붙는다
*  */
@SpringBootTest(classes = {SecurityTestApp.class, RequestLogFilter.class})
@MockSecurityServices        // 로그인 핸들러·계정 상태 확인이 쓰는 서비스는 가짜. 이 테스트는 actuator 주소만 본다 (#161)
class ActuatorSecurityTest {

    /** ActiveMemberFilter 가 쓰는 계정 상태 확인 (@MockSecurityServices 의 가짜). 이 테스트의 회원은 정상 계정이다 */
    @Autowired
    private MemberService memberService;

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        RequestLogFilter requestLogFilter = context.getBean(RequestLogFilter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestLogFilter)
                .apply(springSecurity())
                .build();
    }

    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    @Test
    void health_는_로그인_없이_UP_이고_세부_정보는_숨긴다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void info_는_로그인_없이_앱_이름을_보여_준다() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("moneybeetle"));
    }

    @Test
    void 그_외_actuator_주소는_로그인_안_하면_로그인_화면으로() throws Exception {
        // /api/ 가 아닌 주소라 SecurityErrorHandler 가 401 JSON 대신 로그인 화면으로 보낸다
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
        mockMvc.perform(get("/actuator/beans")).andExpect(status().is3xxRedirection());
    }

    @Test
    void 그_외_actuator_주소는_로그인해도_열려_있지_않다() throws Exception {
        mockMvc.perform(get("/actuator/env").with(user(member()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").with(user(member()))).andExpect(status().isNotFound());
    }
}
