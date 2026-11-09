package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   오늘의 가상 시세 (API 56) : 현재 턴 번호와 종목별 시세
*  */

import java.util.List;
import lombok.Getter;

@Getter
public class StockListDTO {

    private final int turnNo;
    private final List<StockDTO> stocks;

    private StockListDTO(int turnNo, List<StockDTO> stocks) {
        this.turnNo = turnNo;
        this.stocks = stocks;
    }

    public static StockListDTO of(int turnNo, List<StockDTO> stocks) {
        return new StockListDTO(turnNo, stocks);
    }
}
