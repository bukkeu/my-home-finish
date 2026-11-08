package com.wanted.moneybeetle.domain.trading.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 season_turn (턴 진행 기록)
*  */
@Entity
@Table(name = "season_turn")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seasonTurnId;

    private Long tradingSeasonId;

    private int turnNo;

    private LocalDate tradeDate;

    private LocalDateTime progressedAt;

    /** 넘긴 관리자 (member 도메인 - ID 참조) */
    private Long progressedBy;

    private SeasonTurn(Long tradingSeasonId, int turnNo, LocalDate tradeDate) {
        this.tradingSeasonId = tradingSeasonId;
        this.turnNo = turnNo;
        this.tradeDate = tradeDate;
    }

    public static SeasonTurn create(Long tradingSeasonId, int turnNo, LocalDate tradeDate) {
        return new SeasonTurn(tradingSeasonId, turnNo, tradeDate);
    }

    public void recordProgress(Long adminId, LocalDateTime progressedAt) {
        this.progressedBy = adminId;
        this.progressedAt = progressedAt;
    }

    public boolean isProgressed() {
        return this.progressedAt != null;
    }
}
