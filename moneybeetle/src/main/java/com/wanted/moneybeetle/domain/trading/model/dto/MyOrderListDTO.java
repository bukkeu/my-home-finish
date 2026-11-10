package com.wanted.moneybeetle.domain.trading.model.dto;

import java.util.List;

/* comment.
*   담당 : PM (#25)
*   내 거래 내역 (API 61) : 전체 건수와 한 쪽. reason 은 고른 매매 이유 이름
*  */
public record MyOrderListDTO(long totalCount, List<Item> items) {

    public record Item(Long orderId, int turnNo, String stockName, String orderType, int quantity, int price,
            String reason) {
    }
}
