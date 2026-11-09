package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   관리자 시즌 목록의 한 줄 (API 114)
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeasonSummaryDTO {

    private Long seasonId;
    private String name;
    private LocalDate startDate;
    private String status;
    private int currentTurn;
    private long participants;

    public static SeasonSummaryDTO from(TradingSeason season, long participants) {
        return new SeasonSummaryDTO(
                season.getTradingSeasonId(),
                season.getName(),
                season.getStartDate(),
                season.getStatus(),
                season.getCurrentTurn(),
                participants
        );
    }
}
