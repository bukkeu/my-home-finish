package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 trade_reason
*  */
@Entity
@Table(name = "trade_reason")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeReasonId;

    /** 선택지 문구 (예: 나눠 담으려고) */
    private String label;

    private int sortOrder;

    private boolean isActive;

    private TradeReason(String label, int sortOrder) {
        this.label = label;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    public static TradeReason create(String label, int sortOrder) {
        return new TradeReason(label, sortOrder);
    }

    public void changeLabel(String label) {
        this.label = label;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
