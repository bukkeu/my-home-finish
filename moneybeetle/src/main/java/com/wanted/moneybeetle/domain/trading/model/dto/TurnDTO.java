package com.wanted.moneybeetle.domain.trading.model.dto;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import java.time.LocalDate;
import java.time.LocalDateTime;

/* comment.
*   담당 : PM (#44)
*   턴 달력 한 줄 (API 118). state : DONE(지난 턴) · CURRENT(지금 턴) · UPCOMING(남은 턴)
*  */
public record TurnDTO(int turnNo, LocalDate tradeDate, String state, LocalDateTime progressedAt,
        String progressedBy) {

    public static final String DONE = "DONE";
    public static final String CURRENT = "CURRENT";
    public static final String UPCOMING = "UPCOMING";

    public static TurnDTO of(SeasonTurn turn, int currentTurn, String progressedBy) {
        String state = turn.getTurnNo() < currentTurn ? DONE : turn.getTurnNo() == currentTurn ? CURRENT : UPCOMING;
        return new TurnDTO(turn.getTurnNo(), turn.getTradeDate(), state, turn.getProgressedAt(), progressedBy);
    }
}
