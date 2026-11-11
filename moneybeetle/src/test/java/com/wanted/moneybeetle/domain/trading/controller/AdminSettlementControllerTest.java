package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingPublishedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingRewardResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatisticsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SettleResultDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SettlementService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#46)
*   AdminSettlementController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·응답 JSON 모양 (API 명세서 138~141), 에러 응답, 실행한 관리자는 세션에서
*  */
class AdminSettlementControllerTest {

    private static final String BASE = "/api/v1/admin/trading/seasons/7";

    private final SettlementService service = mock(SettlementService.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc mockMvc() {
        var json = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        return MockMvcBuilders.standaloneSetup(new AdminSettlementController(service))
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
    void 정산_실행은_세션의_관리자_번호로_부르고_참가자_수와_정산_시각을_준다() throws Exception {
        loginAsAdmin(1L);
        when(service.settle(7L, 1L)).thenReturn(new SettleResultDTO(1240, LocalDateTime.of(2026, 11, 15, 0, 10)));

        mockMvc().perform(post(BASE + "/settle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants").value(1240))
                .andExpect(jsonPath("$.data.settledAt").value("2026-11-15T00:10:00"));
        verify(service).settle(7L, 1L);
    }

    @Test
    void 이미_정산했으면_409() throws Exception {
        loginAsAdmin(1L);
        when(service.settle(7L, 1L)).thenThrow(new BusinessException(TradingErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc().perform(post(BASE + "/settle"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void 랭킹_공개는_공개_시각을_주고_정산_전이면_NOT_SETTLED() throws Exception {
        loginAsAdmin(1L);
        when(service.publishRanking(7L, 1L)).thenReturn(new RankingPublishedDTO(LocalDateTime.of(2026, 11, 15, 9, 0)));
        mockMvc().perform(post(BASE + "/rankings/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedAt").value("2026-11-15T09:00:00"));

        when(service.publishRanking(8L, 1L)).thenThrow(new BusinessException(TradingErrorCode.NOT_SETTLED));
        mockMvc().perform(post("/api/v1/admin/trading/seasons/8/rankings/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_SETTLED"));
    }

    @Test
    void 보상_지급은_새로_지급한_수상_수를_준다() throws Exception {
        loginAsAdmin(1L);
        when(service.grantRankingRewards(7L, 1L)).thenReturn(new RankingRewardResultDTO(6));

        mockMvc().perform(post(BASE + "/rewards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.awarded").value(6));
    }

    @Test
    void 시즌_통계는_응답_모양대로_돌려준다() throws Exception {
        Map<String, BigDecimal> ratio = new LinkedHashMap<>();
        ratio.put("뉴스를 보고", new BigDecimal("41.2"));
        when(service.findStatistics(7L)).thenReturn(new SeasonStatisticsDTO(new BigDecimal("9.20"),
                List.of("한빛반도체"), ratio, new BigDecimal("18.5")));

        mockMvc().perform(get(BASE + "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avgReturnRate").value(9.2))
                .andExpect(jsonPath("$.data.topBought[0]").value("한빛반도체"))
                .andExpect(jsonPath("$.data.reasonRatio['뉴스를 보고']").value(41.2))
                .andExpect(jsonPath("$.data.panicSellRate").value(18.5));
    }
}
