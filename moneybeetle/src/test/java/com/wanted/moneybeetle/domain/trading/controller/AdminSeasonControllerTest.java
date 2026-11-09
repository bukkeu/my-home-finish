package com.wanted.moneybeetle.domain.trading.controller;

import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonCreatedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatusDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SeasonAdminService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#40)
*   AdminSeasonController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·응답 JSON 모양 (API 명세서 114~117), 201 / 204, 에러 응답
*  */
class AdminSeasonControllerTest {

    private static final String BASE = "/api/v1/admin/trading/seasons";

    private final SeasonAdminService service = mock(SeasonAdminService.class);

    private MockMvc mockMvc() {
        // Spring Boot 서버와 같은 JSON 설정 (날짜를 "2026-11-16" 글자로 내보낸다)
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        return MockMvcBuilders.standaloneSetup(new AdminSeasonController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void 시즌_목록은_items_로_감싸서_돌려준다() throws Exception {
        when(service.findSeasons()).thenReturn(List.of(
                new SeasonSummaryDTO(7L, "시즌 7", LocalDate.of(2026, 11, 2), "RUNNING", 5, 1240L)));

        mockMvc().perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].seasonId").value(7))
                .andExpect(jsonPath("$.data.items[0].startDate").value("2026-11-02"))
                .andExpect(jsonPath("$.data.items[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data.items[0].currentTurn").value(5))
                .andExpect(jsonPath("$.data.items[0].participants").value(1240));
    }

    @Test
    void 시즌_생성은_201과_턴_달력을_돌려준다() throws Exception {
        when(service.createSeason(any(), any())).thenReturn(new SeasonCreatedDTO(8L,
                List.of(new SeasonCreatedDTO.TurnDTO(1, LocalDate.of(2026, 11, 16)))));

        mockMvc().perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"시즌 8\",\"startDate\":\"2026-11-16\",\"copyFromSeasonId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.seasonId").value(8))
                .andExpect(jsonPath("$.data.turns[0].turnNo").value(1))
                .andExpect(jsonPath("$.data.turns[0].tradeDate").value("2026-11-16"));
    }

    @Test
    void 시즌_생성은_로그인한_관리자_번호를_만든_사람으로_넘긴다() throws Exception {
        AuthDetails admin = mock(AuthDetails.class);
        when(admin.getMemberId()).thenReturn(3L);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(admin, null, List.of())));
        when(service.createSeason(any(), eq(3L))).thenReturn(new SeasonCreatedDTO(8L, List.of()));

        mockMvc().perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"시즌 8\",\"startDate\":\"2026-11-16\"}"))
                .andExpect(status().isCreated());

        verify(service).createSeason(any(), eq(3L));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 월요일이_아니면_400_START_DATE_NOT_MONDAY() throws Exception {
        when(service.createSeason(any(), any()))
                .thenThrow(new BusinessException(TradingErrorCode.START_DATE_NOT_MONDAY));

        mockMvc().perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"시즌 8\",\"startDate\":\"2026-11-17\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("START_DATE_NOT_MONDAY"));
    }

    @Test
    void 설정_변경은_204() throws Exception {
        mockMvc().perform(patch(BASE + "/7/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carryoverRate\":100.0,\"carryoverCap\":null,\"unlockLevel\":8}"))
                .andExpect(status().isNoContent());

        verify(service).changeSetting(eq(7L), any());
    }

    @Test
    void 순서를_벗어난_상태_변경은_409_INVALID_STATUS_TRANSITION() throws Exception {
        doThrow(new BusinessException(TradingErrorCode.INVALID_STATUS_TRANSITION))
                .when(service).changeStatus(eq(7L), any(SeasonStatusDTO.class));

        mockMvc().perform(patch(BASE + "/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENDED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }
}
