package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   종목 상세 (API 57) : 현재 가격, 1턴부터 현재 턴까지 가격 흐름, 기업정보, 내 보유
*   - myHolding : 이 시즌에 참가하지 않았거나 그 종목을 갖고 있지 않으면 null
*  */

import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

@Getter
public class StockDetailDTO {

    private final Long stockId;
    private final String name;
    private final String sectorName;
    private final int turnNo;
    private final Integer price;
    private final BigDecimal changeRate;
    private final List<PricePoint> priceHistory;
    private final Company company;
    private final MyHolding myHolding;

    private StockDetailDTO(Long stockId, String name, String sectorName, int turnNo, Integer price,
            BigDecimal changeRate, List<PricePoint> priceHistory, Company company, MyHolding myHolding) {
        this.stockId = stockId;
        this.name = name;
        this.sectorName = sectorName;
        this.turnNo = turnNo;
        this.price = price;
        this.changeRate = changeRate;
        this.priceHistory = priceHistory;
        this.company = company;
        this.myHolding = myHolding;
    }

    /** history 는 1턴부터 현재 턴까지 턴 순서. 마지막이 현재 턴이면 그 값이 현재 가격 */
    public static StockDetailDTO of(Stock stock, String sectorName, int turnNo, List<StockPrice> history,
            Holding holding) {
        StockPrice current = history.isEmpty() || history.get(history.size() - 1).getTurnNo() != turnNo
                ? null : history.get(history.size() - 1);
        return new StockDetailDTO(stock.getStockId(), stock.getName(), sectorName, turnNo,
                current == null ? null : current.getPrice(),
                current == null ? null : current.getChangeRate(),
                history.stream().map(price -> new PricePoint(price.getTurnNo(), price.getPrice())).toList(),
                new Company(stock.getIntro(), stock.getBusiness(), stock.getRevenue(), stock.getOperatingProfit()),
                holding == null || holding.isEmpty() ? null : new MyHolding(holding.getQuantity(), holding.getAvgPrice()));
    }

    @Getter
    public static class PricePoint {

        private final int turnNo;
        private final int price;

        private PricePoint(int turnNo, int price) {
            this.turnNo = turnNo;
            this.price = price;
        }
    }

    @Getter
    public static class Company {

        private final String intro;
        private final String business;
        private final String revenue;
        private final String operatingProfit;

        private Company(String intro, String business, String revenue, String operatingProfit) {
            this.intro = intro;
            this.business = business;
            this.revenue = revenue;
            this.operatingProfit = operatingProfit;
        }
    }

    @Getter
    public static class MyHolding {

        private final int quantity;
        private final int avgPrice;

        private MyHolding(int quantity, int avgPrice) {
            this.quantity = quantity;
            this.avgPrice = avgPrice;
        }
    }
}
