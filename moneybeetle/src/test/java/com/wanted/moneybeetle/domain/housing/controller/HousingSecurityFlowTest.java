package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.config.SecurityConfig;
import com.wanted.moneybeetle.domain.auth.handler.AuthFailHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthLogoutSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.auth.model.service.LoginAuthenticationProvider;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositPolicyDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositResultDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.global.config.WebConfig;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import com.wanted.moneybeetle.global.security.LevelCheckInterceptor;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안 / 모의 청약)
*   #4·#27·#28 연결 확인 : 실제 SecurityConfig + WebConfig(LevelCheckInterceptor) + HousingController 를 함께 띄운다
*   - DB 대신 HousingService·GrowthService 는 가짜, 로그인은 AuthDetails(회원 8번)를 세션에 넣은 것으로 대신한다
*   - 확인 : 로그인 안 함 401 / Lv.8 은 청약 LEVEL_LOCKED / Lv.12 는 통과 + 로그인 회원 번호로 조회
*            / 청약통장은 Lv.1 도 통과 / 납입 POST 는 CSRF 토큰이 있어야 한다
*  */
@SpringBootTest(classes = {HousingSecurityFlowTest.TestApp.class, SecurityConfig.class, SecurityErrorHandler.class,
        AuthSuccessHandler.class, AuthFailHandler.class, AuthLogoutSuccessHandler.class,
        WebConfig.class, LevelCheckInterceptor.class, HousingController.class, GlobalExceptionHandler.class})
class HousingSecurityFlowTest {

    private static final Long MEMBER_ID = 8L;

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
    }

    @MockitoBean
    private HousingService housingService;
    @MockitoBean
    private GrowthService growthService;
    /** 로그인 핸들러(#6)가 필요로 하는 것들. 이 테스트는 로그인 뒤 흐름만 보므로 가짜로 둔다 */
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private LoginAuthenticationProvider loginAuthenticationProvider;
    /** SecurityConfig 의 ActiveMemberFilter(#8)가 요청마다 계정 상태를 확인한다. 이 테스트의 회원은 정상 계정이다 */
    @MockitoBean
    private com.wanted.moneybeetle.domain.member.model.service.MemberService memberService;

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(memberService.isActiveMember(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // 회원 8번(MEMBER)으로 로그인한 세션
    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(MEMBER_ID);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/housing/complexes").param("month", "2026-11"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void Lv8_회원은_모의청약_API가_LEVEL_LOCKED() throws Exception {
        when(growthService.getLevel(MEMBER_ID)).thenReturn(8);

        mockMvc.perform(get("/api/v1/housing/complexes").param("month", "2026-11").with(user(member())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEVEL_LOCKED"));
        verify(housingService, never()).findCalendar(any(), any());
    }

    @Test
    void Lv12_회원은_통과하고_로그인_회원_번호로_조회한다() throws Exception {
        when(growthService.getLevel(MEMBER_ID)).thenReturn(12);
        when(housingService.findCalendar(YearMonth.of(2026, 11), MEMBER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/housing/complexes").param("month", "2026-11").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
        verify(housingService).findCalendar(YearMonth.of(2026, 11), MEMBER_ID);
    }

    @Test
    void 청약통장은_Lv1_이어도_로그인_회원_번호로_조회한다() throws Exception {
        when(growthService.getLevel(MEMBER_ID)).thenReturn(1);
        when(housingService.getAccount(MEMBER_ID)).thenReturn(new AccountDTO(
                1L, 0L, 0L, 0, null, false, DepositPolicyDTO.current(), 12_000L));

        mockMvc.perform(get("/api/v1/housing/account").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cash").value(12000));
        verify(growthService, never()).getLevel(any());   // 레벨 조건이 없어서 묻지도 않는다
    }

    @Test
    void 납입_POST_는_CSRF_토큰이_있어야_한다() throws Exception {
        when(housingService.deposit(MEMBER_ID, 20_000L)).thenReturn(new DepositResultDTO(
                1L, 20_000L, 20_000L, 20_000L, 20_000L, 1, 0L));

        mockMvc.perform(post("/api/v1/housing/account/deposits").with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 20000}"))
                .andExpect(status().isForbidden());              // 토큰 없음 → Security 가 막음

        mockMvc.perform(post("/api/v1/housing/account/deposits").with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 20000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paidCount").value(1));
        verify(housingService).deposit(MEMBER_ID, 20_000L);
    }

    @Test
    void 공고_상세에서_로그인_회원의_신청_여부를_넘긴다() throws Exception {
        when(growthService.getLevel(MEMBER_ID)).thenReturn(12);
        mockMvc.perform(get("/api/v1/housing/complexes/1").with(user(member())))
                .andExpect(status().isOk());   // 막혔다면(401·403) 여기서 바로 원인이 보인다
        verify(housingService).getComplex(1L, MEMBER_ID);
    }
}
