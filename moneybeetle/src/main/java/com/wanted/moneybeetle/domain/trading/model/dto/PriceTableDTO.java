package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   턴별 가격표 (API 127) : 종목(행) x 1~12턴(열). 비어 있는 칸은 null
*   - currentTurn 이하의 턴은 잠겨 있다 (화면에서 수정 칸을 막는 데 쓴다)
*  */

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PriceTableDTO {

    private int currentTurn;
    private List<Row> rows;

    @Getter
    @AllArgsConstructor
    public static class Row {
        private Long stockId;
        private String stockName;
        private List<Integer> prices;
    }
}
