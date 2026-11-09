package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : PM (모의투자)
*   테이블 stock_price (시즌 x 종목 x 턴)
*  */
@Entity
@Table(name = "stock_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockPriceId;

    private Long tradingSeasonId;

    private Long stockId;

    private int turnNo;

    private int price;

    /** 지난 턴 대비 변동률(%), 1턴은 없음 */
    private BigDecimal changeRate;

    // TODO(#2): BaseTimeEntity 가 생기면 상속으로 바꾼다
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

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
