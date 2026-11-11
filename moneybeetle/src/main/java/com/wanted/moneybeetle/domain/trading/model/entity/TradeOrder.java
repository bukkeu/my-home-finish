package com.wanted.moneybeetle.domain.trading.model.entity;

import com.wanted.moneybeetle.global.common.CreatedTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trade_order (체결 후 불변)
*   created_at 은 CreatedTimeEntity 가 공통 시계(Asia/Seoul)로 기록한다. 서버 시간대가 UTC 여도 "오늘 주문 수"가 어긋나지 않는다
*  */
@Entity
@Table(name = "trade_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeOrder extends CreatedTimeEntity {

    public static final String TYPE_BUY = "BUY";
    public static final String TYPE_SELL = "SELL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeOrderId;

    private Long seasonParticipantId;

    private Long stockId;

    /** 체결 턴 */
    private int turnNo;

    private String orderType;

    private int quantity;

    /** 체결가 (그 턴의 고정 가격) */
    private int price;

    /** 체결 금액 = 수량 x 가격 */
    private long amount;

    private Long tradeReasonId;

    private TradeOrder(Long seasonParticipantId, Long stockId, int turnNo, String orderType,
            int quantity, int price, Long tradeReasonId) {
        this.seasonParticipantId = seasonParticipantId;
        this.stockId = stockId;
        this.turnNo = turnNo;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.amount = (long) quantity * price;
        this.tradeReasonId = tradeReasonId;
    }

    public static TradeOrder createBuy(Long seasonParticipantId, Long stockId, int turnNo,
            int quantity, int price, Long tradeReasonId) {
        return new TradeOrder(seasonParticipantId, stockId, turnNo, TYPE_BUY, quantity, price,
                tradeReasonId);
    }

    public static TradeOrder createSell(Long seasonParticipantId, Long stockId, int turnNo,
            int quantity, int price, Long tradeReasonId) {
        return new TradeOrder(seasonParticipantId, stockId, turnNo, TYPE_SELL, quantity, price,
                tradeReasonId);
    }

    public boolean isBuy() {
        return TYPE_BUY.equals(this.orderType);
    }

    public boolean isSell() {
        return TYPE_SELL.equals(this.orderType);
    }
}
