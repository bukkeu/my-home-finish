package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   통합 검색(#15)의 종목 한 줄 : 종목 번호·이름·분야 이름
*  */

import lombok.Getter;

@Getter
public class StockSearchDTO {

    private final Long stockId;
    private final String name;
    private final String sectorName;

    private StockSearchDTO(Long stockId, String name, String sectorName) {
        this.stockId = stockId;
        this.name = name;
        this.sectorName = sectorName;
    }

    public static StockSearchDTO of(Long stockId, String name, String sectorName) {
        return new StockSearchDTO(stockId, name, sectorName);
    }
}
