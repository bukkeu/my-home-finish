package com.wanted.moneybeetle.domain.trading.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.MyOrderListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.OrderRequestDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.OrderResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradeReasonDTO;
import com.wanted.moneybeetle.domain.trading.model.service.OrderService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : PM (모의투자)
*   GET /api/v1/trading/trade-reasons (59), POST /orders (60), GET /orders (61) - #25
*   모의투자 Lv.8 해금 (LevelCheckInterceptor 가 확인). POST 는 Idempotency-Key 헤더로 중복 요청을 막는다 (IdempotencyFilter)
*   화면 : /user/demo-trading
*  */
@RestController
@RequestMapping("/api/v1/trading")
@RequireLevel(UnlockFeature.TRADING)
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // API 59 매매 이유 선택지
    @GetMapping("/trade-reasons")
    public ApiResponse<Map<String, List<TradeReasonDTO>>> findTradeReasons() {
        return ApiResponse.success(Map.of("reasons", orderService.findTradeReasons()));
    }

    // API 60 매수·매도 (201). 회원 번호는 주소·본문이 아니라 로그인 세션에서 꺼낸다 (NFR-03)
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResultDTO> placeOrder(@Valid @RequestBody OrderRequestDTO request,
            @AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(orderService.placeOrder(me == null ? null : me.getMemberId(), request));
    }

    // API 61 내 거래 내역
    @GetMapping("/orders")
    public ApiResponse<MyOrderListDTO> findMyOrders(@RequestParam(required = false) Long seasonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + OrderService.DEFAULT_SIZE) int size,
            @AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(orderService.findMyOrders(me == null ? null : me.getMemberId(), seasonId,
                page, size));
    }
}
