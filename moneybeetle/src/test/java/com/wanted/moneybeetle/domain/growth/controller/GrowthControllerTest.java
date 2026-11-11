package com.wanted.moneybeetle.domain.growth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.ExpHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.LevelDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   GrowthController 출석 현황·캐시 내역·경험치 내역 (이슈 #12·#10, API 21~23) - 서버·DB 없이 MockMvc 로 돈다
*  */
class GrowthControllerTest {

    private final AttendanceService attendanceService = mock(AttendanceService.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final GrowthService growthService = mock(GrowthService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new GrowthController(attendanceService, rewardService, growthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("출석 현황은 로그인한 회원의 번호로 조회하고 명세서 형식으로 돌려준다")
    void getAttendance() throws Exception {
        when(attendanceService.getStatus(7L)).thenReturn(new AttendanceStatusDTO(3, 2, 10_000, true));

        mockMvc.perform(get("/api/v1/members/me/attendance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak").value(3))
                .andExpect(jsonPath("$.data.daysToReward").value(2))
                .andExpect(jsonPath("$.data.rewardCash").value(10_000))
                .andExpect(jsonPath("$.data.checkedToday").value(true));

        verify(attendanceService).getStatus(7L);
    }

    // ---- 캐시·경험치 내역 (이슈 #10, API 22·23) ----

    /** 서비스가 받은 Pageable 로 빈 페이지를 돌려주게 해서, 컨트롤러가 정한 page·size 를 응답에서도 확인한다 */
    private void givenEmptyHistories() {
        when(rewardService.getBalanceSnapshot(7L)).thenReturn(MemberBalanceDTO.of(7L, 3, 420, 35_000L));
        when(rewardService.findCashHistories(eq(7L), any(), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<CashHistoryDTO>(List.of(), call.getArgument(2), 0));
        when(rewardService.findExpHistories(eq(7L), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<ExpHistoryDTO>(List.of(), call.getArgument(1), 0));
    }

    private Pageable capturedCashPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(rewardService).findCashHistories(eq(7L), any(), captor.capture());
        return captor.getValue();
    }

    private Pageable capturedExpPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(rewardService).findExpHistories(eq(7L), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("캐시 내역은 로그인한 회원 번호로 조회하고, 요청에 회원 번호를 실어 보내도 무시한다. 잔액과 페이지 정보를 돌려준다")
    void cashHistoriesUseLoggedInMember() throws Exception {
        givenEmptyHistories();

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("memberId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cash").value(35_000))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));

        verify(rewardService).getBalanceSnapshot(7L);
        verify(rewardService, never()).findCashHistories(eq(99L), any(), any());
        verify(rewardService, never()).getBalanceSnapshot(99L);
    }

    @Test
    @DisplayName("캐시 내역: size 를 안 보내면 20, 100 보다 크면 100, 1 보다 작으면 1, 음수 page 는 0 으로 보정한다")
    void cashHistoryPagingIsClamped() throws Exception {
        givenEmptyHistories();

        mockMvc.perform(get("/api/v1/members/me/cash-histories")).andExpect(status().isOk());
        assertThat(capturedCashPageable().getPageSize()).isEqualTo(20);
        assertThat(capturedCashPageable().getPageNumber()).isZero();
        clearInvocations(rewardService);

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("size", "100")).andExpect(status().isOk());
        assertThat(capturedCashPageable().getPageSize()).isEqualTo(100);   // 최대값 그대로
        clearInvocations(rewardService);

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("size", "500")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
        assertThat(capturedCashPageable().getPageSize()).isEqualTo(100);
        clearInvocations(rewardService);

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("size", "0")).andExpect(status().isOk());
        assertThat(capturedCashPageable().getPageSize()).isEqualTo(1);
        clearInvocations(rewardService);

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("size", "-7").param("page", "-3"))
                .andExpect(status().isOk());
        Pageable negative = capturedCashPageable();
        assertThat(negative.getPageSize()).isEqualTo(1);
        assertThat(negative.getPageNumber()).isZero();   // 음수 page 는 0
    }

    @Test
    @DisplayName("캐시 내역: type 은 서비스에 그대로 넘기고, 보내지 않으면 null (전체)")
    void cashHistoryTypeIsPassedThrough() throws Exception {
        givenEmptyHistories();

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("type", "EARN")).andExpect(status().isOk());
        verify(rewardService).findCashHistories(eq(7L), eq("EARN"), any(Pageable.class));

        mockMvc.perform(get("/api/v1/members/me/cash-histories")).andExpect(status().isOk());
        verify(rewardService).findCashHistories(eq(7L), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("캐시 내역: 빈칸이 아닌 잘못된 type 은 400 INVALID_HISTORY_TYPE 으로 응답한다 (서비스 예외가 MockMvc 응답으로 나온다)")
    void invalidCashHistoryTypeIs400() throws Exception {
        when(rewardService.getBalanceSnapshot(7L)).thenReturn(MemberBalanceDTO.of(7L, 3, 420, 35_000L));
        when(rewardService.findCashHistories(eq(7L), eq("BOGUS"), any(Pageable.class)))
                .thenThrow(new BusinessException(GrowthErrorCode.INVALID_HISTORY_TYPE));

        mockMvc.perform(get("/api/v1/members/me/cash-histories").param("type", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_HISTORY_TYPE"));
    }

    @Test
    @DisplayName("경험치 내역은 로그인한 회원 번호로 조회하고, 현재 레벨·경험치와 페이지 정보를 돌려준다")
    void expHistoriesUseLoggedInMember() throws Exception {
        givenEmptyHistories();

        mockMvc.perform(get("/api/v1/members/me/exp-histories").param("memberId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value(3))
                .andExpect(jsonPath("$.data.exp").value(420))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(0));

        verify(rewardService).getBalanceSnapshot(7L);
        verify(rewardService, never()).findExpHistories(eq(99L), any());
        verify(rewardService, never()).getBalanceSnapshot(99L);
    }

    @Test
    @DisplayName("경험치 내역: size 를 안 보내면 20, 100 보다 크면 100, 1 보다 작으면 1, 음수 page 는 0 으로 보정한다")
    void expHistoryPagingIsClamped() throws Exception {
        givenEmptyHistories();

        mockMvc.perform(get("/api/v1/members/me/exp-histories")).andExpect(status().isOk());
        assertThat(capturedExpPageable().getPageSize()).isEqualTo(20);
        clearInvocations(rewardService);

        mockMvc.perform(get("/api/v1/members/me/exp-histories").param("size", "1000")).andExpect(status().isOk());
        assertThat(capturedExpPageable().getPageSize()).isEqualTo(100);
        clearInvocations(rewardService);

        mockMvc.perform(get("/api/v1/members/me/exp-histories").param("size", "0").param("page", "-1"))
                .andExpect(status().isOk());
        Pageable clamped = capturedExpPageable();
        assertThat(clamped.getPageSize()).isEqualTo(1);
        assertThat(clamped.getPageNumber()).isZero();
    }

    @Test
    @DisplayName("레벨·칭호 표 (API 24): { levels: [...] } 로 돌려주고, 해금 기능이 없는 레벨은 unlocks 를 생략한다")
    void levels() throws Exception {
        when(growthService.findLevels()).thenReturn(List.of(
                LevelDTO.of(LevelPolicy.create(1, 0, "고시원", 0), List.of()),
                LevelDTO.of(LevelPolicy.create(8, 1250, "투룸", 80_000), List.of("TRADING"))));

        mockMvc.perform(get("/api/v1/levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.levels.length()").value(2))
                .andExpect(jsonPath("$.data.levels[0].level").value(1))
                .andExpect(jsonPath("$.data.levels[0].requiredExp").value(0))
                .andExpect(jsonPath("$.data.levels[0].title").value("고시원"))
                .andExpect(jsonPath("$.data.levels[0].unlocks").doesNotExist())
                .andExpect(jsonPath("$.data.levels[1].requiredExp").value(1250))
                .andExpect(jsonPath("$.data.levels[1].levelupCash").value(80000))
                .andExpect(jsonPath("$.data.levels[1].unlocks[0]").value("TRADING"));
    }
}
