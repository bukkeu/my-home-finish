package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnCheckDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnAdvancedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnCalendarDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnDTO;
import com.wanted.moneybeetle.domain.trading.model.service.TurnAdminService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#44)
*   AdminTurnController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·응답 JSON 모양 (API 명세서 118~120), 에러 응답, 넘긴 관리자는 세션에서
*  */
class AdminTurnControllerTest {

    private static final String BASE = "/api/v1/admin/trading/seasons/7/turns";

    private final TurnAdminService service = mock(TurnAdminService.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        return MockMvcBuilders.standaloneSetup(new AdminTurnController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    private void loginAsAdmin(long memberId) {
        AuthDetails admin = mock(AuthDetails.class);
        when(admin.getMemberId()).thenReturn(memberId);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(admin, null, List.of())));
    }

    @Test
    void 턴_달력은_턴_목록과_휴장일을_돌려준다() throws Exception {
        when(service.findTurns(7L)).thenReturn(new TurnCalendarDTO(7L, "RUNNING", 4, 12,
                List.of(new TurnDTO(4, LocalDate.of(2026, 11, 5), "CURRENT", LocalDateTime.of(2026, 11, 5, 0, 0, 3),
                        "김운영")),
                List.of(LocalDate.of(2026, 11, 8))));

        mockMvc().perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentTurn").value(4))
                .andExpect(jsonPath("$.data.turns[0].turnNo").value(4))
                .andExpect(jsonPath("$.data.turns[0].tradeDate").value("2026-11-05"))
                .andExpect(jsonPath("$.data.turns[0].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.turns[0].progressedBy").value("김운영"))
                .andExpect(jsonPath("$.data.holidays[0]").value("2026-11-08"));
    }

    @Test
    void 다음_턴_점검은_ready_와_비어_있는_종목을_돌려준다() throws Exception {
        when(service.checkNextTurn(7L)).thenReturn(
                new NextTurnCheckDTO(false, 6, LocalDate.of(2026, 11, 7), List.of("모아모빌리티", "별숲엔터"), 2, false, true, true));

        mockMvc().perform(get(BASE + "/next-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.nextTurn").value(6))
                .andExpect(jsonPath("$.data.nextTradeDate").value("2026-11-07"))
                .andExpect(jsonPath("$.data.missingPrices[1]").value("별숲엔터"))
                .andExpect(jsonPath("$.data.newsCount").value(2))
                .andExpect(jsonPath("$.data.isHoliday").value(false));
    }

    @Test
    void 다음_턴_진행은_세션의_관리자_번호로_넘기고_현재_턴을_돌려준다() throws Exception {
        loginAsAdmin(1L);
        when(service.advanceTurn(7L, 5, 1L)).thenReturn(new TurnAdvancedDTO(6));

        mockMvc().perform(post(BASE + "/next").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentTurn\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTurn").value(6));
        verify(service).advanceTurn(7L, 5, 1L);
    }

    @Test
    void 현재_턴을_보내지_않으면_400_이고_서비스를_부르지_않는다() throws Exception {
        loginAsAdmin(1L);

        mockMvc().perform(post(BASE + "/next").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        verifyNoInteractions(service);
    }

    @Test
    void 점검_실패와_현재_턴_불일치는_409_로_응답한다() throws Exception {
        loginAsAdmin(1L);
        when(service.advanceTurn(eq(7L), eq(5), anyLong()))
                .thenThrow(new BusinessException(TradingErrorCode.NEXT_TURN_NOT_READY));
        when(service.advanceTurn(eq(7L), eq(4), anyLong()))
                .thenThrow(new BusinessException(TradingErrorCode.TURN_CONFLICT));

        mockMvc().perform(post(BASE + "/next").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentTurn\": 5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NEXT_TURN_NOT_READY"));
        mockMvc().perform(post(BASE + "/next").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentTurn\": 4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TURN_CONFLICT"));
    }
}
