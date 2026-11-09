package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositPolicyDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositResultDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import com.wanted.moneybeetle.global.security.LevelCheckInterceptor;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingController #27·#28 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·파라미터·본문 연결과 응답 JSON 모양 (API 명세서 63~67)
*   - @RequireLevel(HOUSING) : 레벨 조회 연동(#4 TODO) 전이라 지금은 LEVEL_LOCKED 로 막힌다
*  */
class HousingControllerTest {

    private final HousingService housingService = mock(HousingService.class);

    private MockMvc mockMvc(boolean withLevelCheck) {
        // Spring Boot 서버와 같은 JSON 설정 (Boot 는 날짜를 "2026-11-03" 글자로 내보낸다)
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        var builder = MockMvcBuilders.standaloneSetup(new HousingController(housingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())   // Boot 처럼 Pageable 받기
                .setMessageConverters(json);
        if (withLevelCheck) {
            builder.addInterceptors(new LevelCheckInterceptor());
        }
        return builder.build();
    }

    @Test
    void 캘린더는_month_로_조회하고_items_로_감싸서_돌려준다() throws Exception {
        when(housingService.findCalendar(eq(YearMonth.of(2026, 11)), any())).thenReturn(List.of(
                new ComplexDTO(1L, "그린숲 행복주택", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3),
                        LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17), "OPEN", false)));

        mockMvc(false).perform(get("/api/v1/housing/complexes").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].complexId").value(1))
                .andExpect(jsonPath("$.data.items[0].applyStartDate").value("2026-11-03"))
                .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[0].applied").value(false));
    }

    @Test
    void 공고_상세는_주소의_complexId_로_조회한다() throws Exception {
        when(housingService.getComplex(eq(1L), any())).thenReturn(new ComplexDetailDTO(
                1L, "그린숲 행복주택", "가상시 덕양구", "59㎡", 320000000L, 120, "일반공급",
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17),
                "OPEN", null, null));

        mockMvc(false).perform(get("/api/v1/housing/complexes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complexId").value(1))
                .andExpect(jsonPath("$.data.salePrice").value(320000000))
                .andExpect(jsonPath("$.data.competitionRate").doesNotExist())
                .andExpect(jsonPath("$.data.myApplication").doesNotExist());
    }

    @Test
    void 레벨_조회_연동_전에는_LEVEL_LOCKED_403() throws Exception {
        mockMvc(true).perform(get("/api/v1/housing/complexes/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEVEL_LOCKED"));
    }

    // ── #28 청약통장 (레벨 조건 없음 : 레벨 검사를 켜도 막히지 않아야 한다) ──

    @Test
    void 내_청약통장은_레벨_검사를_켜도_막히지_않는다() throws Exception {
        when(housingService.getAccount(any())).thenReturn(new AccountDTO(
                1L, 1_840_000L, 1_590_000L, 14, LocalDate.of(2026, 11, 4), false,
                DepositPolicyDTO.current(), 1_250_000L));

        mockMvc(true).perform(get("/api/v1/housing/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidCount").value(14))
                .andExpect(jsonPath("$.data.lastPaidDate").value("2026-11-04"))
                .andExpect(jsonPath("$.data.depositPolicy.minAmount").value(20000))
                .andExpect(jsonPath("$.data.cash").value(1250000));
    }

    @Test
    void 납입은_JSON_본문의_amount_로_받고_201_로_답한다() throws Exception {
        when(housingService.deposit(any(), eq(300_000L))).thenReturn(new DepositResultDTO(
                15L, 300_000L, 250_000L, 2_140_000L, 1_840_000L, 15, 950_000L));

        mockMvc(true).perform(post("/api/v1/housing/account/deposits")
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

        mockMvc(true).perform(post("/api/v1/housing/account/deposits")
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
        when(housingService.findDeposits(any(), pageable.capture())).thenReturn(PageResponse.from(page));

        mockMvc(true).perform(get("/api/v1/housing/account/deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].paidDate").value("2026-11-05"))
                .andExpect(jsonPath("$.data.items[0].paidCountAfter").value(15))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("paidDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
