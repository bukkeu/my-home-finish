package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   시세 한 줄 (가격, 등락률) - API 56
*   - 가격이 아직 입력되지 않은 종목은 price·changeRate 가 null, 1턴은 changeRate 가 null
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class StockDTO {

    private final Long stockId;
    private final String name;
    private final String sectorName;
    private final Integer price;
    private final BigDecimal changeRate;

    private StockDTO(Long stockId, String name, String sectorName, Integer price, BigDecimal changeRate) {
        this.stockId = stockId;
        this.name = name;
        this.sectorName = sectorName;
        this.price = price;
        this.changeRate = changeRate;
    }

    public static StockDTO of(Stock stock, String sectorName, StockPrice price) {
        return new StockDTO(stock.getStockId(), stock.getName(), sectorName,
                price == null ? null : price.getPrice(),
                price == null ? null : price.getChangeRate());
    }
}
