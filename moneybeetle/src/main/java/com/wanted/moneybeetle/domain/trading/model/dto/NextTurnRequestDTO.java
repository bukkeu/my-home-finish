package com.wanted.moneybeetle.domain.trading.model.dto;

import jakarta.validation.constraints.NotNull;

/* comment.
*   담당 : PM (#44)
*   다음 턴 진행 요청 (API 120). 화면이 보고 있던 현재 턴 - 서버 현재 턴과 다르면 TURN_CONFLICT
*  */
public record NextTurnRequestDTO(@NotNull(message = "현재 턴을 보내 주세요") Integer expectedCurrentTurn) {
}
