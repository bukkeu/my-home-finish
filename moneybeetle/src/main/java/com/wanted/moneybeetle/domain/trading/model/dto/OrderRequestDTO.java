package com.wanted.moneybeetle.domain.trading.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/* comment.
*   담당 : PM (#25)
*   주문 요청 (API 60) : 종목, BUY/SELL, 수량(1 이상 정수), 화면이 본 턴, 매매 이유
*   매매 이유(reasonId)는 비어 있어도 요청은 받고, 서비스가 REASON_REQUIRED 로 알려 준다 (SP-57)
*  */
public record OrderRequestDTO(
        @NotNull(message = "종목을 골라 주세요") Long stockId,
        @NotNull(message = "매수 또는 매도를 골라 주세요")
        @Pattern(regexp = "BUY|SELL", message = "매수(BUY) 또는 매도(SELL)만 고를 수 있어요") String orderType,
        @Min(value = 1, message = "수량은 1주 이상이어야 해요") int quantity,
        @NotNull(message = "현재 턴을 보내 주세요") Integer turnNo,
        Long reasonId) {
}
