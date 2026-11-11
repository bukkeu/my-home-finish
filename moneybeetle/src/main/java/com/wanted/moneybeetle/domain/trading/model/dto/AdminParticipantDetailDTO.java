package com.wanted.moneybeetle.domain.trading.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/* comment.
*   담당 : PM (#45)
*   관리자 참가자 상세 (API 136) : 요약, 보유 종목, 거래 내역·매매 이유
*  */
public record AdminParticipantDetailDTO(Long participantId, Long seasonId, String nickname, int level,
        long investAmount, long cash, long netAsset, BigDecimal returnRate, List<HoldingItem> holdings,
        List<OrderItem> orders) {

    /** 보유 종목 한 줄. evalAmount = quantity x currentPrice (현재 턴 가격) */
    public record HoldingItem(Long stockId, String stockName, int quantity, int avgPrice, int currentPrice,
            long evalAmount) {
    }

    /** 거래 내역 한 줄. reason 은 매매 이유 선택지 이름 */
    public record OrderItem(Long orderId, int turnNo, String stockName, String orderType, int quantity, int price,
            long amount, String reason, LocalDateTime createdAt) {
    }
}
