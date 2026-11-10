package com.wanted.moneybeetle.domain.trading.model.dto;

import java.time.LocalDate;
import java.util.List;

/* comment.
*   담당 : PM (#44)
*   턴 달력·진행 기록 (API 118). holidays 는 시즌 기간 중 휴장일(일요일)
*  */
public record TurnCalendarDTO(Long seasonId, String status, int currentTurn, int totalTurns,
        List<TurnDTO> turns, List<LocalDate> holidays) {
}
