package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   분야 목록 (API 55) : 분야와 소속 종목 이름
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import java.util.List;
import lombok.Getter;

@Getter
public class SectorStocksDTO {

    private final Long sectorId;
    private final String name;
    private final List<StockName> stocks;

    private SectorStocksDTO(Long sectorId, String name, List<StockName> stocks) {
        this.sectorId = sectorId;
        this.name = name;
        this.stocks = stocks;
    }

    public static SectorStocksDTO of(Sector sector, List<Stock> stocks) {
        return new SectorStocksDTO(sector.getSectorId(), sector.getName(),
                stocks.stream().map(stock -> new StockName(stock.getStockId(), stock.getName())).toList());
    }

    @Getter
    public static class StockName {

        private final Long stockId;
        private final String name;

        private StockName(Long stockId, String name) {
            this.stockId = stockId;
            this.name = name;
        }
    }
}
