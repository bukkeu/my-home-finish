package com.wanted.moneybeetle.domain.trading.model.dto;

import java.math.BigDecimal;
import java.util.List;

/* comment.
*   담당 : PM (#45)
*   관리자 참가자 목록 (API 135) : 닉네임, 레벨, 투자 가능 금액, 현재 순자산, 수익률
*  */
public record AdminParticipantListDTO(long totalCount, List<Item> items) {

    public record Item(Long participantId, String nickname, int level, long investAmount, long netAsset,
            BigDecimal returnRate) {
    }
}
