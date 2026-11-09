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
*   테이블 season_participant (투자 가능 금액, 정산, 이월금)
*  */
@Entity
@Table(name = "season_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seasonParticipantId;

    private Long tradingSeasonId;

    /** 회원 (member 도메인 - ID 참조) */
    private Long memberId;

    /** 참가 때 가져온 캐시 */
    private long savedCash;

    /** 지난 시즌 이월금 */
    private long carryoverIn;

    /** 투자 가능 금액 = 캐시 + 이월금 (참가 후 변경 불가) */
    private long investAmount;

    /** 현재 보유 현금 */
    private long cash;

    private Long finalNetAsset;

    private BigDecimal returnRate;

    private Integer assetRank;

    private Integer returnRank;

    /** 다음 시즌 이월금 */
    private long carryoverOut;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime joinedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private SeasonParticipant(Long tradingSeasonId, Long memberId, long savedCash,
            long carryoverIn) {
        this.tradingSeasonId = tradingSeasonId;
        this.memberId = memberId;
        this.savedCash = savedCash;
        this.carryoverIn = carryoverIn;
        this.investAmount = savedCash + carryoverIn;
        this.cash = this.investAmount;
        this.carryoverOut = 0L;
    }

    public static SeasonParticipant create(Long tradingSeasonId, Long memberId, long savedCash,
            long carryoverIn) {
        return new SeasonParticipant(tradingSeasonId, memberId, savedCash, carryoverIn);
    }

    /** 매수 체결: 현금 차감. 잔액 확인은 주문 처리(#25)에서 먼저 한다 */
    public void payCash(long amount) {
        this.cash -= amount;
    }

    /** 매도 체결: 현금 증가 */
    public void receiveCash(long amount) {
        this.cash += amount;
    }

    public boolean hasEnoughCash(long amount) {
        return this.cash >= amount;
    }

    public void settle(long finalNetAsset, BigDecimal returnRate, long carryoverOut) {
        this.finalNetAsset = finalNetAsset;
        this.returnRate = returnRate;
        this.carryoverOut = carryoverOut;
    }

    public void changeRanks(Integer assetRank, Integer returnRank) {
        this.assetRank = assetRank;
        this.returnRank = returnRank;
    }

    public boolean isSettled() {
        return this.finalNetAsset != null;
    }
}
