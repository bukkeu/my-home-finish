package com.wanted.moneybeetle.domain.trading.model.dto;

import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;

/* comment.
*   담당 : PM (#25)
*   매매 이유 선택지 한 줄 (API 59)
*  */
public record TradeReasonDTO(Long reasonId, String label) {

    public static TradeReasonDTO from(TradeReason reason) {
        return new TradeReasonDTO(reason.getTradeReasonId(), reason.getLabel());
    }
}
