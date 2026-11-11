package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dto.MyApplicationDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositPolicyDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositResultDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingApplicationService;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.security.LevelCheckInterceptor;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingController #27·#28 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·파라미터·본문 연결과 응답 JSON 모양 (API 명세서 63~67)
*   - 로그인 회원 번호는 세션(@AuthenticationPrincipal AuthDetails)에서 꺼내 Service 에 넘긴다 (회원 8번으로 로그인한 상태)
*   - @RequireLevel(HOUSING) : Lv.12 부터 통과, Lv.11 은 LEVEL_LOCKED. 청약통장 API 는 레벨과 무관
*  */
class HousingControllerTest {

    private static final Long MEMBER_ID = 8L;

    private final HousingService housingService = mock(HousingService.class);
    private final HousingApplicationService applicationService = mock(HousingApplicationService.class);
    private final GrowthService growthService = mock(GrowthService.class);

    @BeforeEach
    void login() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(MEMBER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    // 로그인한 회원의 레벨을 정하고 MockMvc 를 만든다 (레벨 검사 인터셉터 포함)
    private MockMvc mockMvcAtLevel(int level) {
        when(growthService.getLevel(MEMBER_ID)).thenReturn(level);
        // Spring Boot 서버와 같은 JSON 설정 (Boot 는 날짜를 "2026-11-03" 글자로 내보낸다)
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        return MockMvcBuilders.standaloneSetup(new HousingController(housingService, applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(),     // Boot 처럼 Pageable 받기
                        new AuthenticationPrincipalArgumentResolver())                       // @AuthenticationPrincipal 받기
                .setMessageConverters(json)
                .addInterceptors(new LevelCheckInterceptor(growthService,
                        new UnlockLevelProvider(mock(SystemSettingService.class))))   // 설정 없음 → 기본 Lv.12
                .build();
    }

    @Test
    void 캘린더는_로그인_회원_번호로_조회하고_items_로_감싸서_돌려준다() throws Exception {
        when(housingService.findCalendar(YearMonth.of(2026, 11), MEMBER_ID)).thenReturn(List.of(
                new ComplexDTO(1L, "그린숲 행복주택", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                        LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17), "OPEN", true)));

        mockMvcAtLevel(12).perform(get("/api/v1/housing/complexes").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].complexId").value(1))
                .andExpect(jsonPath("$.data.items[0].applyStartDate").value("2026-11-03"))
                .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[0].applied").value(true));   // 로그인 회원이 신청한 단지
    }

    @Test
    void 공고_상세는_로그인_회원의_신청_정보를_보여준다() throws Exception {
        when(housingService.getComplex(1L, MEMBER_ID)).thenReturn(new ComplexDetailDTO(
                1L, "그린숲 행복주택", "가상시 덕양구", "59㎡", 320000000L, 120, "일반공급",
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17),
                "OPEN", null,
                new MyApplicationDTO(40L, "MB-261105-0042", 24, LocalDateTime.of(2026, 11, 5, 10, 0))));

        mockMvcAtLevel(12).perform(get("/api/v1/housing/complexes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complexId").value(1))
                .andExpect(jsonPath("$.data.salePrice").value(320000000))
                .andExpect(jsonPath("$.data.competitionRate").doesNotExist())
                .andExpect(jsonPath("$.data.myApplication.receiptNo").value("MB-261105-0042"));
    }

    @Test
    void 모의청약_API는_Lv11_이면_LEVEL_LOCKED_403() throws Exception {
        mockMvcAtLevel(11).perform(get("/api/v1/housing/complexes/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEVEL_LOCKED"));
        verify(housingService, never()).getComplex(any(), any());
    }

    // ── #28 청약통장 (레벨 조건 없음 : Lv.1 로 로그인해도 막히지 않아야 한다) ──

    @Test
    void 내_청약통장은_Lv1_이어도_로그인_회원_번호로_조회한다() throws Exception {
        when(housingService.getAccount(MEMBER_ID)).thenReturn(new AccountDTO(
                1L, 1_840_000L, 1_590_000L, 14, LocalDate.of(2026, 11, 4), false,
                DepositPolicyDTO.current(), 1_250_000L));

        mockMvcAtLevel(1).perform(get("/api/v1/housing/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidCount").value(14))
                .andExpect(jsonPath("$.data.lastPaidDate").value("2026-11-04"))
                .andExpect(jsonPath("$.data.depositPolicy.minAmount").value(20000))
                .andExpect(jsonPath("$.data.cash").value(1250000));
    }

    @Test
    void 납입은_JSON_본문의_amount_로_받고_201_로_답한다() throws Exception {
        when(housingService.deposit(MEMBER_ID, 300_000L)).thenReturn(new DepositResultDTO(
                15L, 300_000L, 250_000L, 2_140_000L, 1_840_000L, 15, 950_000L));

        mockMvcAtLevel(1).perform(post("/api/v1/housing/account/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.depositId").value(15))
                .andExpect(jsonPath("$.data.recognizedAmount").value(250000))
                .andExpect(jsonPath("$.data.paidCount").value(15));
    }

    @Test
    void 같은_날_두_번째_납입은_409_ALREADY_PAID_TODAY() throws Exception {
        when(housingService.deposit(any(), anyLong()))
                .thenThrow(new BusinessException(HousingErrorCode.ALREADY_PAID_TODAY));

        mockMvcAtLevel(1).perform(post("/api/v1/housing/account/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PAID_TODAY"))
                .andExpect(jsonPath("$.error.message").value("오늘은 이미 납입했어요"));
    }

    @Test
    void 납입_내역은_기본_20개씩_납입일_최신순으로_조회한다() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        Page<DepositDTO> page = new PageImpl<>(
                List.of(new DepositDTO(15L, LocalDate.of(2026, 11, 5), 300_000, 250_000, 15)),
                PageRequest.of(0, 20), 1);
        when(housingService.findDeposits(eq(MEMBER_ID), pageable.capture())).thenReturn(PageResponse.from(page));

        mockMvcAtLevel(1).perform(get("/api/v1/housing/account/deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].paidDate").value("2026-11-05"))
                .andExpect(jsonPath("$.data.items[0].paidCountAfter").value(15))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("paidDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // ── #29 가점·신청·결과 ──

    @Test
    void 가점_계산기는_본문을_받아_로그인_회원으로_계산한다_Lv11_은_막힌다() throws Exception {
        when(applicationService.previewScore(eq(MEMBER_ID), any())).thenReturn(
                new com.wanted.moneybeetle.domain.housing.model.dto.ScoreDTO(8, 10, 6, 24, 84, LocalDate.of(2026, 11, 9)));

        mockMvcAtLevel(12).perform(post("/api/v1/housing/score-preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"homelessYears\": 3, \"dependents\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(24))
                .andExpect(jsonPath("$.data.maxScore").value(84))
                .andExpect(jsonPath("$.data.baseDate").value("2026-11-09"));

        mockMvcAtLevel(11).perform(post("/api/v1/housing/score-preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"homelessYears\": 3, \"dependents\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEVEL_LOCKED"));
    }

    @Test
    void 청약_신청은_주소의_단지와_본문을_받아_201() throws Exception {
        when(applicationService.apply(eq(MEMBER_ID), eq(1L), any())).thenReturn(
                new com.wanted.moneybeetle.domain.housing.model.dto.ApplyResultDTO(40L, "MB-261105-0042", 24));

        mockMvcAtLevel(12).perform(post("/api/v1/housing/complexes/1/applications")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"homelessYears\": 3, \"dependents\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.receiptNo").value("MB-261105-0042"));
    }

    @Test
    void 중복_신청은_409_ALREADY_APPLIED() throws Exception {
        when(applicationService.apply(eq(MEMBER_ID), eq(1L), any()))
                .thenThrow(new BusinessException(HousingErrorCode.ALREADY_APPLIED));

        mockMvcAtLevel(12).perform(post("/api/v1/housing/complexes/1/applications")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"homelessYears\": 3, \"dependents\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_APPLIED"));
    }

    @Test
    void 내_신청_내역은_기본_20개씩_신청_최신순() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(applicationService.findMyApplications(eq(MEMBER_ID), pageable.capture())).thenReturn(PageResponse.from(
                new PageImpl<>(List.of(new com.wanted.moneybeetle.domain.housing.model.dto.ApplicationDTO(
                        40L, "그린숲 행복주택", "MB-261105-0042", 24, LocalDate.of(2026, 11, 17), null)),
                        PageRequest.of(0, 20), 1)));

        mockMvcAtLevel(12).perform(get("/api/v1/housing/applications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].complexName").value("그린숲 행복주택"))
                .andExpect(jsonPath("$.data.items[0].result").doesNotExist());

        assertThat(pageable.getValue().getSort().getOrderFor("appliedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 당첨_결과는_레벨과_무관하고_발표_전이면_403_NOT_ANNOUNCED_YET() throws Exception {
        when(applicationService.getResult(MEMBER_ID, 40L)).thenReturn(
                new com.wanted.moneybeetle.domain.housing.model.dto.ApplicationResultDTO(
                        "LOSE", 24, "이번에는 낙첨이에요. 내 가점은 24점이었어요"));
        mockMvcAtLevel(1).perform(get("/api/v1/housing/applications/40/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("LOSE"));

        when(applicationService.getResult(MEMBER_ID, 41L))
                .thenThrow(new BusinessException(HousingErrorCode.NOT_ANNOUNCED_YET));
        mockMvcAtLevel(1).perform(get("/api/v1/housing/applications/41/result"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_ANNOUNCED_YET"));
    }
}
