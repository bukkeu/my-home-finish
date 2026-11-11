package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.service.RankingService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#26)
*   RankingController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·쿼리(type, page, size)·응답 JSON 모양 (API 명세서 62), 공개 전 403, 로그인 회원 번호는 세션에서
*  */
class RankingControllerTest {

    private final RankingService service = mock(RankingService.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new RankingController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void login(long memberId) {
        AuthDetails member = mock(AuthDetails.class);
        when(member.getMemberId()).thenReturn(memberId);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(member, null, List.of())));
    }

    @Test
    void 랭킹은_쿼리를_그대로_넘기고_응답_모양대로_돌려준다() throws Exception {
        login(8L);
        when(service.findRankings(6L, "RETURN", 1, 20, 8L)).thenReturn(new RankingDTO("시즌 6", "RETURN", 23,
                List.of(new RankingDTO.Item(1, "초록개미", "내 집", 8_920_400, new BigDecimal("42.80"), true)),
                new RankingDTO.Me(23, 4_610_000, new BigDecimal("6.50"))));

        mockMvc().perform(get("/api/v1/trading/seasons/6/rankings?type=RETURN&page=1&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seasonName").value("시즌 6"))
                .andExpect(jsonPath("$.data.type").value("RETURN"))
                .andExpect(jsonPath("$.data.totalCount").value(23))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("초록개미"))
                .andExpect(jsonPath("$.data.items[0].netAsset").value(8_920_400))
                .andExpect(jsonPath("$.data.items[0].returnRate").value(42.8))
                .andExpect(jsonPath("$.data.items[0].hasBadge").value(true))
                .andExpect(jsonPath("$.data.me.rank").value(23));
    }

    @Test
    void 쿼리가_없으면_기본값으로_부르고_로그인_정보가_없으면_null_을_넘긴다() throws Exception {
        when(service.findRankings(6L, null, 0, 10, null))
                .thenReturn(new RankingDTO("시즌 6", "ASSET", 0, List.of(), null));

        mockMvc().perform(get("/api/v1/trading/seasons/6/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        verify(service).findRankings(6L, null, 0, 10, null);
    }

    @Test
    void 공개_전이면_403_RANKING_NOT_PUBLISHED() throws Exception {
        when(service.findRankings(6L, null, 0, 10, null))
                .thenThrow(new BusinessException(TradingErrorCode.RANKING_NOT_PUBLISHED));

        mockMvc().perform(get("/api/v1/trading/seasons/6/rankings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RANKING_NOT_PUBLISHED"));
    }
}
