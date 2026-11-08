package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   시즌 생성 결과 (API 115) : 새 시즌 번호와 자동으로 만든 12턴 달력
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeasonCreatedDTO {

    private Long seasonId;
    private List<TurnDTO> turns;

    public static SeasonCreatedDTO from(TradingSeason season, List<SeasonTurn> turns) {
        return new SeasonCreatedDTO(
                season.getTradingSeasonId(),
                turns.stream().map(TurnDTO::from).toList()
        );
    }

    @Getter
    @AllArgsConstructor
    public static class TurnDTO {
        private int turnNo;
        private LocalDate tradeDate;

        static TurnDTO from(SeasonTurn turn) {
            return new TurnDTO(turn.getTurnNo(), turn.getTradeDate());
        }
    }
}
