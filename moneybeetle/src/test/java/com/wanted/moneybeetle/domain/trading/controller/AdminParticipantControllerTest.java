package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.service.AdminParticipantService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#45)
*   AdminParticipantController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·쿼리(sort, page, size, type)·응답 JSON 모양 (API 명세서 135~137), 에러 응답
*  */
class AdminParticipantControllerTest {

    private static final String BASE = "/api/v1/admin/trading";

    private final AdminParticipantService service = mock(AdminParticipantService.class);

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        return MockMvcBuilders.standaloneSetup(new AdminParticipantController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void 참가자_목록은_쿼리를_그대로_넘기고_응답_모양대로_돌려준다() throws Exception {
        when(service.findParticipants(7L, "returnRate", 1, 20)).thenReturn(new AdminParticipantListDTO(120,
                List.of(new AdminParticipantListDTO.Item(120L, "민지", 8, 4_820_000, 5_134_000,
                        new BigDecimal("6.50")))));

        mockMvc().perform(get(BASE + "/seasons/7/participants?sort=returnRate&page=1&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(120))
                .andExpect(jsonPath("$.data.items[0].participantId").value(120))
                .andExpect(jsonPath("$.data.items[0].nickname").value("민지"))
                .andExpect(jsonPath("$.data.items[0].level").value(8))
                .andExpect(jsonPath("$.data.items[0].investAmount").value(4_820_000))
                .andExpect(jsonPath("$.data.items[0].netAsset").value(5_134_000))
                .andExpect(jsonPath("$.data.items[0].returnRate").value(6.5));
    }

    @Test
    void 쿼리가_없으면_기본값_쪽0_크기50_으로_부른다() throws Exception {
        when(service.findParticipants(7L, null, 0, 50)).thenReturn(new AdminParticipantListDTO(0, List.of()));

        mockMvc().perform(get(BASE + "/seasons/7/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        verify(service).findParticipants(7L, null, 0, 50);
    }

    @Test
    void 참가자_상세는_보유_종목과_거래_내역을_돌려준다() throws Exception {
        when(service.getParticipant(120L)).thenReturn(new AdminParticipantDetailDTO(120L, 7L, "민지", 8, 4_820_000,
                500_000, 5_134_000, new BigDecimal("6.50"),
                List.of(new AdminParticipantDetailDTO.HoldingItem(1L, "한빛반도체", 6, 84_500, 96_950, 581_700)),
                List.of(new AdminParticipantDetailDTO.OrderItem(901L, 5, "한빛반도체", "BUY", 6, 84_500, 507_000,
                        "뉴스를 보고", LocalDateTime.of(2026, 11, 5, 10, 0)))));

        mockMvc().perform(get(BASE + "/participants/120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantId").value(120))
                .andExpect(jsonPath("$.data.holdings[0].stockName").value("한빛반도체"))
                .andExpect(jsonPath("$.data.holdings[0].evalAmount").value(581_700))
                .andExpect(jsonPath("$.data.orders[0].orderType").value("BUY"))
                .andExpect(jsonPath("$.data.orders[0].reason").value("뉴스를 보고"))
                .andExpect(jsonPath("$.data.orders[0].createdAt").value("2026-11-05T10:00:00"));
    }

    @Test
    void 없는_참가자는_404() throws Exception {
        when(service.getParticipant(999L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc().perform(get(BASE + "/participants/999")).andExpect(status().isNotFound());
    }

    @Test
    void 랭킹_미리보기는_시즌_랭킹과_같은_모양이다() throws Exception {
        when(service.previewRankings(7L, "RETURN", 0, 10)).thenReturn(new RankingDTO("시즌 7", "RETURN", 2,
                List.of(new RankingDTO.Item(1, "초록개미", null, 8_920_400, new BigDecimal("42.80"), false)), null));

        mockMvc().perform(get(BASE + "/seasons/7/rankings/preview?type=RETURN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seasonName").value("시즌 7"))
                .andExpect(jsonPath("$.data.type").value("RETURN"))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].hasBadge").value(false))
                .andExpect(jsonPath("$.data.me").doesNotExist());
    }
}
