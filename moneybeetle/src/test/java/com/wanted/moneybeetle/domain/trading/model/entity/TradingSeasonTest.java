package com.wanted.moneybeetle.domain.trading.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (#40)
*   TradingSeason 규칙 확인 : 기본 설정, 12턴 거래일(일요일 제외), 상태 순서(SP-65), 설정 잠금, 턴 진행·정산·랭킹 공개(#111)
*  */
class TradingSeasonTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private TradingSeason season() {
        return TradingSeason.create("시즌 8", MONDAY, 1L);
    }

    @Test
    void 기본_설정은_이월_100퍼센트_상한_없음() {
        TradingSeason season = season();

        assertThat(season.getCarryoverRate()).isEqualByComparingTo("100.00");
        assertThat(season.getCarryoverCap()).isNull();
        assertThat(season.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(season.isReady()).isTrue();
    }

    @Test
    void 거래일은_일요일을_빼고_12일이다() {
        List<LocalDate> dates = season().tradeDates();

        assertThat(dates).hasSize(12);
        assertThat(dates.get(0)).isEqualTo(MONDAY);
        assertThat(dates.get(5)).isEqualTo(LocalDate.of(2026, 11, 21));   // 1주차 토요일
        assertThat(dates.get(6)).isEqualTo(LocalDate.of(2026, 11, 23));   // 일요일 건너뛰고 월요일
        assertThat(dates.get(11)).isEqualTo(LocalDate.of(2026, 11, 28));  // 2주차 토요일
        assertThat(dates).noneMatch(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY);
    }

    @Test
    void n턴의_예정_거래일은_일요일을_건너뛴다() {
        TradingSeason season = season();

        assertThat(season.tradeDateOf(1)).isEqualTo(MONDAY);
        assertThat(season.tradeDateOf(6)).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(season.tradeDateOf(7)).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(season.tradeDateOf(12)).isEqualTo(LocalDate.of(2026, 11, 28));
    }

    @Test
    void 정해진_순서로_상태를_바꿀_수_있다() {
        TradingSeason season = season();

        season.changeStatus(TradingSeason.STATUS_RUNNING);
        season.changeStatus(TradingSeason.STATUS_HOLIDAY);
        season.changeStatus(TradingSeason.STATUS_RUNNING);     // 일요일 휴장 뒤 다시 진행
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        season.changeStatus(TradingSeason.STATUS_ENDED);

        assertThat(season.isEnded()).isTrue();
    }

    @Test
    void 순서를_건너뛰거나_되돌리면_INVALID_STATUS_TRANSITION() {
        TradingSeason season = season();

        assertThatThrownBy(() -> season.changeStatus(TradingSeason.STATUS_ENDED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION);

        season.changeStatus(TradingSeason.STATUS_RUNNING);
        assertThatThrownBy(() -> season.changeStatus(TradingSeason.STATUS_READY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> season.changeStatus("PAUSED"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 다음_상태가_없으면_바꿀_수_없다고_답한다() {
        assertThat(season().canChangeStatusTo(null)).isFalse();
    }

    @Test
    void 설정은_바꿀_수_있지만_정산_중에는_SEASON_CLOSED() {
        TradingSeason season = season();
        season.changeSetting(new BigDecimal("50.00"), 300_000L);
        assertThat(season.getCarryoverRate()).isEqualByComparingTo("50.00");
        assertThat(season.getCarryoverCap()).isEqualTo(300_000L);

        season.changeStatus(TradingSeason.STATUS_RUNNING);
        season.changeStatus(TradingSeason.STATUS_SETTLING);

        assertThatThrownBy(() -> season.changeSetting(new BigDecimal("100.00"), null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.SEASON_CLOSED);
    }

    // ===== #111 턴 진행·정산·랭킹 공개 규칙 =====

    private TradingSeason runningSeason() {
        TradingSeason season = season();
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        return season;
    }

    @Test
    void 진행_중이면_턴이_하나씩_넘어간다() {
        TradingSeason season = runningSeason();

        season.advanceTurn();
        season.advanceTurn();

        assertThat(season.getCurrentTurn()).isEqualTo(2);
    }

    @Test
    void 마지막_턴_이후에는_턴을_넘길_수_없다() {
        TradingSeason season = runningSeason();
        for (int i = 0; i < season.getTotalTurns(); i++) {
            season.advanceTurn();
        }

        assertThatThrownBy(season::advanceTurn)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.LAST_TURN_REACHED));
        assertThat(season.getCurrentTurn()).isEqualTo(12);
    }

    @Test
    void 휴장일에는_턴을_넘길_수_없다() {
        TradingSeason season = runningSeason();
        season.changeStatus(TradingSeason.STATUS_HOLIDAY);

        assertThatThrownBy(season::advanceTurn)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_HOLIDAY));
        assertThat(season.getCurrentTurn()).isZero();
    }

    @Test
    void 준비_정산_종료_상태에서는_턴을_넘길_수_없다() {
        TradingSeason ready = season();
        TradingSeason settling = runningSeason();
        settling.changeStatus(TradingSeason.STATUS_SETTLING);
        TradingSeason ended = runningSeason();                  // 진행 중 -> 정산 중 -> (정산·공개) -> 종료
        ended.changeStatus(TradingSeason.STATUS_SETTLING);
        ended.completeSettlement(LocalDateTime.of(2026, 11, 30, 0, 0));
        ended.publishRanking(LocalDateTime.of(2026, 11, 30, 0, 1));
        assertThat(ended.isEnded()).isTrue();

        for (TradingSeason season : List.of(ready, settling, ended)) {
            assertThatThrownBy(season::advanceTurn)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION));
        }
    }

    @Test
    void 정산은_정산_중에만_기록하고_랭킹_공개는_정산을_마친_뒤에만_한다() {
        TradingSeason running = runningSeason();
        LocalDateTime now = LocalDateTime.of(2026, 11, 30, 0, 0);

        assertThatThrownBy(() -> running.completeSettlement(now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION));

        TradingSeason season = runningSeason();
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        assertThatThrownBy(() -> season.publishRanking(now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION));

        season.completeSettlement(now);
        season.publishRanking(now.plusMinutes(1));

        assertThat(season.isEnded()).isTrue();
        assertThat(season.isRankingPublished()).isTrue();
    }

    @Test
    void 랭킹을_공개하지_않은_진행_중_시즌은_공개할_수_없다() {
        TradingSeason season = runningSeason();

        assertThatThrownBy(() -> season.publishRanking(LocalDateTime.of(2026, 11, 30, 0, 0)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.INVALID_STATUS_TRANSITION));
        assertThat(season.isEnded()).isFalse();
    }
}
