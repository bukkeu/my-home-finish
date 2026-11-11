package com.wanted.moneybeetle.domain.trading.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : PM (모의투자)
*   테이블 stock_price (시즌 x 종목 x 턴)
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#293)
*  */
@Entity
@Table(name = "stock_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockPrice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockPriceId;

    private Long tradingSeasonId;

    private Long stockId;

    private int turnNo;

    private int price;

    /** 지난 턴 대비 변동률(%), 1턴은 없음 */
    private BigDecimal changeRate;

    private StockPrice(Long tradingSeasonId, Long stockId, int turnNo, int price,
            BigDecimal changeRate) {
        this.tradingSeasonId = tradingSeasonId;
        this.stockId = stockId;
        this.turnNo = turnNo;
        this.price = price;
        this.changeRate = changeRate;
    }

    public static StockPrice create(Long tradingSeasonId, Long stockId, int turnNo, int price,
            BigDecimal changeRate) {
        return new StockPrice(tradingSeasonId, stockId, turnNo, price, changeRate);
    }

    public void changePrice(int price, BigDecimal changeRate) {
        this.price = price;
        this.changeRate = changeRate;
    }
}
