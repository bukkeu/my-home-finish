package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : PM (모의투자)
*   테이블 holding
*  */
@Entity
@Table(name = "holding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holdingId;

    private Long seasonParticipantId;

    private Long stockId;

    private int quantity;

    /** 평균 매수 단가 (원 단위 내림) */
    private int avgPrice;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private Holding(Long seasonParticipantId, Long stockId) {
        this.seasonParticipantId = seasonParticipantId;
        this.stockId = stockId;
        this.quantity = 0;
        this.avgPrice = 0;
    }

    public static Holding create(Long seasonParticipantId, Long stockId) {
        return new Holding(seasonParticipantId, stockId);
    }

    /** 매수 체결: 수량을 늘리고 평균 단가를 다시 계산한다 (원 단위 내림) */
    public void addBuy(int buyQuantity, int price) {
        long totalCost = (long) this.quantity * this.avgPrice + (long) buyQuantity * price;
        this.quantity += buyQuantity;
        this.avgPrice = (int) (totalCost / this.quantity);
    }

    /** 매도 체결: 수량을 줄인다. 보유 수량 확인은 주문 처리(#25)에서 먼저 한다. 다 팔면 평균 단가를 0으로 */
    public void subtractSell(int sellQuantity) {
        this.quantity -= sellQuantity;
        if (this.quantity == 0) {
            this.avgPrice = 0;
        }
    }

    public boolean hasEnoughQuantity(int sellQuantity) {
        return this.quantity >= sellQuantity;
    }

    public boolean isEmpty() {
        return this.quantity == 0;
    }
}
