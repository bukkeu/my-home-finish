package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.MyOrderListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.OrderRequestDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.OrderResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradeReasonDTO;
import com.wanted.moneybeetle.domain.trading.model.service.OrderService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#25)
*   OrderController 확인 (Service 는 가짜, DB·로그인 없이)
*   - 주소·요청·응답 JSON 모양 (API 명세서 59~61), 201, 입력 검증(400), 에러 응답, 회원 번호는 세션에서
*  */
class OrderControllerTest {

    private static final String BASE = "/api/v1/trading";

    private final OrderService service = mock(OrderService.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new OrderController(service))
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
    void 매매_이유_선택지는_reasons_로_감싸서_준다() throws Exception {
        when(service.findTradeReasons()).thenReturn(
                List.of(new TradeReasonDTO(1L, "뉴스를 보고"), new TradeReasonDTO(2L, "나눠 담으려고")));

        mockMvc().perform(get(BASE + "/trade-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reasons[0].reasonId").value(1))
                .andExpect(jsonPath("$.data.reasons[1].label").value("나눠 담으려고"));
    }

    @Test
    void 주문은_세션의_회원_번호로_넘기고_201_과_체결_결과를_준다() throws Exception {
        login(8L);
        OrderRequestDTO request = new OrderRequestDTO(1L, "BUY", 6, 5, 1L);
        when(service.placeOrder(8L, request)).thenReturn(
                new OrderResultDTO(901L, 84_500, 507_000, 2_432_000, new OrderResultDTO.Holding(1L, 18, 80_833)));

        mockMvc().perform(post(BASE + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":1,\"orderType\":\"BUY\",\"quantity\":6,\"turnNo\":5,\"reasonId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(901))
                .andExpect(jsonPath("$.data.price").value(84_500))
                .andExpect(jsonPath("$.data.amount").value(507_000))
                .andExpect(jsonPath("$.data.cash").value(2_432_000))
                .andExpect(jsonPath("$.data.holding.quantity").value(18))
                .andExpect(jsonPath("$.data.holding.avgPrice").value(80_833));
        verify(service).placeOrder(8L, request);
    }

    @Test
    void 매매_이유_없이도_요청은_받아_서비스가_REASON_REQUIRED_로_알려_준다() throws Exception {
        login(8L);
        when(service.placeOrder(any(), any())).thenThrow(new BusinessException(TradingErrorCode.REASON_REQUIRED));

        mockMvc().perform(post(BASE + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":1,\"orderType\":\"BUY\",\"quantity\":6,\"turnNo\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    }

    @Test
    void 잘못된_주문_형식은_400_이고_서비스를_부르지_않는다() throws Exception {
        login(8L);

        for (String body : List.of(
                "{\"orderType\":\"BUY\",\"quantity\":1,\"turnNo\":5,\"reasonId\":1}",                              // 종목 없음
                "{\"stockId\":1,\"orderType\":\"HOLD\",\"quantity\":1,\"turnNo\":5,\"reasonId\":1}",               // 종류 오류
                "{\"stockId\":1,\"orderType\":\"BUY\",\"quantity\":0,\"turnNo\":5,\"reasonId\":1}",                // 0주
                "{\"stockId\":1,\"orderType\":\"SELL\",\"quantity\":-3,\"turnNo\":5,\"reasonId\":1}",              // 음수
                "{\"stockId\":1,\"orderType\":\"BUY\",\"quantity\":1,\"reasonId\":1}")) {                          // 턴 없음
            mockMvc().perform(post(BASE + "/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void 이전_턴_가격_주문은_409_STALE_TURN_PRICE() throws Exception {
        login(8L);
        when(service.placeOrder(any(), any())).thenThrow(new BusinessException(TradingErrorCode.STALE_TURN_PRICE));

        mockMvc().perform(post(BASE + "/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":1,\"orderType\":\"BUY\",\"quantity\":6,\"turnNo\":4,\"reasonId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_TURN_PRICE"));
    }

    @Test
    void 내_거래_내역은_쿼리를_그대로_넘기고_기본값은_쪽0_크기20() throws Exception {
        login(8L);
        when(service.findMyOrders(8L, 7L, 1, 10)).thenReturn(new MyOrderListDTO(4,
                List.of(new MyOrderListDTO.Item(901L, 5, "한빛반도체", "BUY", 6, 84_500, "뉴스를 보고"))));
        when(service.findMyOrders(8L, null, 0, 20)).thenReturn(new MyOrderListDTO(0, List.of()));

        mockMvc().perform(get(BASE + "/orders?seasonId=7&page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(4))
                .andExpect(jsonPath("$.data.items[0].stockName").value("한빛반도체"))
                .andExpect(jsonPath("$.data.items[0].reason").value("뉴스를 보고"));
        mockMvc().perform(get(BASE + "/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        verify(service).findMyOrders(8L, null, 0, 20);
    }
}
