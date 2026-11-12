package com.wanted.moneybeetle.domain.trading.model.entity;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trading_season
*   상태 : READY -> RUNNING <-> HOLIDAY -> SETTLING -> ENDED (SP-65, 거꾸로 가지 않음.
*          일요일 휴장 뒤에는 다시 진행 중으로 돌아온다)
*          SETTLING -> ENDED 는 상태 변경(changeStatus, API 117)으로 하지 않고 정산(completeSettlement) 뒤
*          랭킹 공개(publishRanking, API 139)로만 한다. 상태 변경으로 바로 끝내면 정산·공개·이월금 확정을 할 수 없게 된다 (#329)
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#293)
*  */
@Entity
@Table(name = "trading_season")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingSeason extends BaseTimeEntity {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_HOLIDAY = "HOLIDAY";
    public static final String STATUS_SETTLING = "SETTLING";
    public static final String STATUS_ENDED = "ENDED";

    /** 상태 변경(API 117)으로 바꿀 수 있는 다음 상태 (SP-65). 정산 중 -> 종료는 랭킹 공개로만 (#329) */
    private static final Map<String, Set<String>> NEXT_STATUSES = Map.of(
            STATUS_READY, Set.of(STATUS_RUNNING),
            STATUS_RUNNING, Set.of(STATUS_HOLIDAY, STATUS_SETTLING),
            STATUS_HOLIDAY, Set.of(STATUS_RUNNING, STATUS_SETTLING),
            STATUS_SETTLING, Set.of(),
            STATUS_ENDED, Set.of());

    /** 시즌 기간: 시작일(월) 포함 14일, 종료일 = 시작일 + 13일 (SP-50) */
    public static final int SEASON_DAYS = 14;
    public static final int DEFAULT_TOTAL_TURNS = 12;
    /** 이월 비율 기본값: 최종 순자산 전액 이월 (SP-63, #76 확정) */
    public static final BigDecimal DEFAULT_CARRYOVER_RATE = new BigDecimal("100.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradingSeasonId;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private int currentTurn;

    private int totalTurns;

    private BigDecimal carryoverRate;

    private Long carryoverCap;

    private LocalDateTime settledAt;

    private LocalDateTime rankingPublishedAt;

    /** 만든 관리자 (member 도메인 - ID 참조) */
    private Long createdBy;

    private TradingSeason(String name, LocalDate startDate, BigDecimal carryoverRate,
            Long carryoverCap, Long createdBy) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = startDate.plusDays(SEASON_DAYS - 1);
        this.status = STATUS_READY;
        this.currentTurn = 0;
        this.totalTurns = DEFAULT_TOTAL_TURNS;
        this.carryoverRate = carryoverRate;
        this.carryoverCap = carryoverCap;
        this.createdBy = createdBy;
    }

    /** 기본 설정(이월 100%, 상한 없음)으로 만든다. 모의투자 해금 레벨은 시즌이 아니라 운영 설정 하나다 (#242) */
    public static TradingSeason create(String name, LocalDate startDate, Long createdBy) {
        return new TradingSeason(name, startDate, DEFAULT_CARRYOVER_RATE, null, createdBy);
    }

    public static TradingSeason create(String name, LocalDate startDate, BigDecimal carryoverRate,
            Long carryoverCap, Long createdBy) {
        return new TradingSeason(name, startDate, carryoverRate, carryoverCap, createdBy);
    }

    /** 이월 비율·상한 (ADM-11). 정산 중이거나 끝난 시즌은 바꿀 수 없다 */
    public void changeSetting(BigDecimal carryoverRate, Long carryoverCap) {
        if (isSettlingOrEnded()) {
            throw new BusinessException(TradingErrorCode.SEASON_CLOSED);
        }
        this.carryoverRate = carryoverRate;
        this.carryoverCap = carryoverCap;
    }

    /** 정해진 순서로만 상태를 바꾼다 (SP-65) */
    public void changeStatus(String nextStatus) {
        if (!canChangeStatusTo(nextStatus)) {
            throw new BusinessException(TradingErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = nextStatus;
    }

    public boolean canChangeStatusTo(String nextStatus) {
        if (nextStatus == null) {
            return false;
        }
        return NEXT_STATUSES.getOrDefault(this.status, Set.of()).contains(nextStatus);
    }

    /** 휴장일 규칙 (SP-50): 일요일. 거래일 계산·턴 달력·다음 턴 점검이 모두 이 메서드를 쓴다 */
    public static boolean isClosedDay(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** 12턴의 거래일: 시작일부터 휴장일(일요일)을 빼고 하루 1턴 (SP-50) */
    public List<LocalDate> tradeDates() {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = this.startDate;
        while (dates.size() < this.totalTurns) {
            if (!isClosedDay(date)) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }

    /** n턴의 예정 거래일 (1~totalTurns) */
    public LocalDate tradeDateOf(int turnNo) {
        return tradeDates().get(turnNo - 1);
    }

    /**
     * 다음 턴으로 넘긴다 (SP-59). 진행 중(RUNNING)일 때만, 마지막 턴(totalTurns) 이후에는 넘길 수 없다.
     * 휴장(HOLIDAY)에는 MARKET_HOLIDAY, 그 밖의 상태에서는 INVALID_STATUS_TRANSITION 으로 거절한다
     */
    public void advanceTurn() {
        checkAdvanceable();
        this.currentTurn++;
    }

    /** 지금 턴을 넘길 수 있는 상태인지 확인한다 (넘길 수 없으면 BusinessException). 가격·뉴스 준비 여부는 Service 가 확인한다 */
    public void checkAdvanceable() {
        if (isHoliday()) {
            throw new BusinessException(TradingErrorCode.MARKET_HOLIDAY);
        }
        if (!isRunning()) {
            throw new BusinessException(TradingErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (isLastTurn()) {
            throw new BusinessException(TradingErrorCode.LAST_TURN_REACHED);
        }
    }

    /** 정산을 마쳤다고 기록한다. 정산 중(SETTLING)에, 한 번만 */
    public void completeSettlement(LocalDateTime settledAt) {
        if (!STATUS_SETTLING.equals(this.status) || isSettled()) {
            throw new BusinessException(TradingErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.settledAt = settledAt;
    }

    /**
     * 랭킹을 공개하고 시즌을 끝낸다. 정산 중(SETTLING)이고 정산을 마친 뒤에만 (SP-62, SP-65).
     * 정산 중이 아니면(이미 끝났거나 아직 정산 단계가 아니면) INVALID_STATUS_TRANSITION, 정산 전이면 NOT_SETTLED
     */
    public void publishRanking(LocalDateTime publishedAt) {
        if (!STATUS_SETTLING.equals(this.status)) {
            throw new BusinessException(TradingErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (!isSettled()) {
            throw new BusinessException(TradingErrorCode.NOT_SETTLED);
        }
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

    public boolean isSettlingOrEnded() {
        return STATUS_SETTLING.equals(this.status) || STATUS_ENDED.equals(this.status);
    }

    public boolean isLastTurn() {
        return this.currentTurn >= this.totalTurns;
    }

    /** 정산을 마쳤는가 (정산은 한 번만 실행한다) */
    public boolean isSettled() {
        return this.settledAt != null;
    }

    /** 정산을 마치고 끝난 시즌인가 (다음 시즌을 시작하기 전에 이전 시즌이 이 상태여야 한다, #207) */
    public boolean isSettledAndEnded() {
        return isEnded() && isSettled();
    }

    public boolean isRankingPublished() {
        return this.rankingPublishedAt != null;
    }
}
