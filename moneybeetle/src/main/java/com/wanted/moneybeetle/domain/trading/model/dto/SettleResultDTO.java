package com.wanted.moneybeetle.domain.trading.model.dto;

import java.time.LocalDateTime;

/* comment.
*   담당 : PM (#46)
*   정산 실행 결과 (API 138) : 정산한 참가자 수, 정산 시각
*  */
public record SettleResultDTO(int participants, LocalDateTime settledAt) {
}
