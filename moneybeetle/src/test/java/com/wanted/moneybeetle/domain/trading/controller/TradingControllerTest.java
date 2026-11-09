package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.service.TradingService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#24)
*   TradingController 종목 상세 확인 (서버·DB 없이 Service 는 가짜(mock))
*   - 로그인 회원 번호를 세션(@AuthenticationPrincipal)에서 꺼내 Service 에 넘긴다 -> 내 보유(myHolding)에 쓰인다
*   - 로그인 정보가 없으면 null 을 넘긴다
*  */
class TradingControllerTest {

    private TradingService tradingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tradingService = mock(TradingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TradingController(tradingService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long memberId) {
        AuthDetails me = mock(AuthDetails.class);
        when(me.getMemberId()).thenReturn(memberId);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(me, null, List.of())));
    }

    @Test
    void 종목_상세는_로그인한_회원의_번호를_넘긴다() throws Exception {
        loginAs(8L);

        mockMvc.perform(get("/api/v1/trading/stocks/1")).andExpect(status().isOk());

        verify(tradingService).getStock(1L, 8L);
    }

    @Test
    void 로그인_정보가_없으면_회원_번호는_null() throws Exception {
        mockMvc.perform(get("/api/v1/trading/stocks/1")).andExpect(status().isOk());

        verify(tradingService).getStock(1L, null);
    }
}
