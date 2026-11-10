package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (#25)
*   체결 결과 (API 60) : 주문 번호, 체결가, 체결 금액, 남은 현금, 그 종목의 보유(체결 후)
*  */
public record OrderResultDTO(Long orderId, int price, long amount, long cash, Holding holding) {

    public record Holding(Long stockId, int quantity, int avgPrice) {
    }
}
