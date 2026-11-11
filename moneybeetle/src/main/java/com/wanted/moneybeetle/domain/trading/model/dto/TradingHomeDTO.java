package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 홈 (시즌·턴 달력, 현금, 순자산, 보유 종목) - API 53
*   - participation : 이 시즌에 참가하지 않았으면 null, holdings 는 빈 목록
*   - calendar.state : DONE(지난 턴), CURRENT(현재 턴), UPCOMING(남은 턴), HOLIDAY(일요일 휴장)
*  */

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class TradingHomeDTO {

    public static final String NOTICE = "모든 투자·청약 화면은 가상 실습이며 실제 금융 거래가 아니에요";

    private final Season season;
    private final Participation participation;
    private final List<HoldingItem> holdings;
    private final String notice;

    private TradingHomeDTO(Season season, Participation participation, List<HoldingItem> holdings) {
        this.season = season;
        this.participation = participation;
        this.holdings = holdings;
        this.notice = NOTICE;
    }

    public static TradingHomeDTO of(Season season, Participation participation, List<HoldingItem> holdings) {
        return new TradingHomeDTO(season, participation, holdings);
    }

    @Getter
    public static class Season {

        private final Long seasonId;
        private final String name;
        private final String status;
        private final int currentTurn;
        private final int totalTurns;
        private final LocalDate endDate;
        private final LocalDateTime nextTurnAt;   // 다음 턴 거래일 08:00 (장 시작, #208). 마지막 턴이면 null
        private final List<CalendarDay> calendar;

        public Season(Long seasonId, String name, String status, int currentTurn, int totalTurns, LocalDate endDate,
                LocalDateTime nextTurnAt, List<CalendarDay> calendar) {
            this.seasonId = seasonId;
            this.name = name;
            this.status = status;
            this.currentTurn = currentTurn;
            this.totalTurns = totalTurns;
            this.endDate = endDate;
            this.nextTurnAt = nextTurnAt;
            this.calendar = calendar;
        }
    }

    @Getter
    public static class CalendarDay {

        public static final String DONE = "DONE";
        public static final String CURRENT = "CURRENT";
        public static final String UPCOMING = "UPCOMING";
        public static final String HOLIDAY = "HOLIDAY";

        private final LocalDate date;
        private final Integer turnNo;   // 휴장일은 null
        private final String state;

        public CalendarDay(LocalDate date, Integer turnNo, String state) {
            this.date = date;
            this.turnNo = turnNo;
            this.state = state;
        }
    }

    @Getter
    public static class Participation {

        private final Long participantId;
        private final long investAmount;
        private final long cash;
        private final long netAsset;
        private final BigDecimal returnRate;

        public Participation(Long participantId, long investAmount, long cash, long netAsset, BigDecimal returnRate) {
            this.participantId = participantId;
            this.investAmount = investAmount;
            this.cash = cash;
            this.netAsset = netAsset;
            this.returnRate = returnRate;
        }
    }

    @Getter
    public static class HoldingItem {

        private final Long stockId;
        private final String stockName;
        private final int quantity;
        private final int avgPrice;
        private final long evaluation;
        private final BigDecimal returnRate;

        public HoldingItem(Long stockId, String stockName, int quantity, int avgPrice, long evaluation,
                BigDecimal returnRate) {
            this.stockId = stockId;
            this.stockName = stockName;
            this.quantity = quantity;
            this.avgPrice = avgPrice;
            this.evaluation = evaluation;
            this.returnRate = returnRate;
        }
    }
}
