package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trading_season (READY -> RUNNING -> HOLIDAY -> SETTLING -> ENDED)
*  */
@Entity
@Table(name = "trading_season")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingSeason {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_HOLIDAY = "HOLIDAY";
    public static final String STATUS_SETTLING = "SETTLING";
    public static final String STATUS_ENDED = "ENDED";

    /** 시즌 기간: 시작일(월) 포함 14일, 종료일 = 시작일 + 13일 */
    public static final int SEASON_DAYS = 14;
    public static final int DEFAULT_TOTAL_TURNS = 12;
    public static final int DEFAULT_UNLOCK_LEVEL = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradingSeasonId;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private int currentTurn;

    private int totalTurns;

    private int unlockLevel;

    private BigDecimal carryoverRate;

    private Long carryoverCap;

    private LocalDateTime settledAt;

    private LocalDateTime rankingPublishedAt;

    /** 만든 관리자 (member 도메인 - ID 참조) */
    private Long createdBy;

    // TODO(#2): BaseTimeEntity 가 생기면 상속으로 바꾼다
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private TradingSeason(String name, LocalDate startDate, BigDecimal carryoverRate,
            Long carryoverCap, Long createdBy) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = startDate.plusDays(SEASON_DAYS - 1);
        this.status = STATUS_READY;
        this.currentTurn = 0;
        this.totalTurns = DEFAULT_TOTAL_TURNS;
        this.unlockLevel = DEFAULT_UNLOCK_LEVEL;
        this.carryoverRate = carryoverRate;
        this.carryoverCap = carryoverCap;
        this.createdBy = createdBy;
    }

    public static TradingSeason create(String name, LocalDate startDate, BigDecimal carryoverRate,
            Long carryoverCap, Long createdBy) {
        return new TradingSeason(name, startDate, carryoverRate, carryoverCap, createdBy);
    }

    public void changeSetting(String name, BigDecimal carryoverRate, Long carryoverCap) {
        this.name = name;
        this.carryoverRate = carryoverRate;
        this.carryoverCap = carryoverCap;
    }

    public void start() {
        this.status = STATUS_RUNNING;
    }

    public void startHoliday() {
        this.status = STATUS_HOLIDAY;
    }

    public void endHoliday() {
        this.status = STATUS_RUNNING;
    }

    public void advanceTurn() {
        this.currentTurn++;
    }

    public void startSettling() {
        this.status = STATUS_SETTLING;
    }

    public void completeSettlement(LocalDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public void publishRanking(LocalDateTime publishedAt) {
        this.rankingPublishedAt = publishedAt;
        this.status = STATUS_ENDED;
    }

    public boolean isReady() {
        return STATUS_READY.equals(this.status);
    }

    public boolean isRunning() {
        return STATUS_RUNNING.equals(this.status);
    }

    public boolean isHoliday() {
        return STATUS_HOLIDAY.equals(this.status);
    }

    public boolean isEnded() {
        return STATUS_ENDED.equals(this.status);
    }

    public boolean isLastTurn() {
        return this.currentTurn >= this.totalTurns;
    }

    public boolean isRankingPublished() {
        return this.rankingPublishedAt != null;
    }
}
