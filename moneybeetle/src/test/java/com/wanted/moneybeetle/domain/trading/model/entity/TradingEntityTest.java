package com.wanted.moneybeetle.domain.trading.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TradingEntityTest {

    @Test
    void 시즌_종료일은_시작일_더하기_13일이고_READY로_만들어진다() {
        TradingSeason season = TradingSeason.create("시즌 1", LocalDate.of(2026, 11, 9),
                new BigDecimal("30.00"), null, 1L);

        assertThat(season.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(season.isReady()).isTrue();
        assertThat(season.getCurrentTurn()).isZero();
        assertThat(season.getTotalTurns()).isEqualTo(12);
    }

    @Test
    void 참가하면_투자_가능_금액은_캐시_더하기_이월금이다() {
        SeasonParticipant participant = SeasonParticipant.create(1L, 10L, 2_000_000L, 939_000L);

        assertThat(participant.getInvestAmount()).isEqualTo(2_939_000L);
        assertThat(participant.getCash()).isEqualTo(2_939_000L);
    }

    @Test
    void 매수하면_평균_단가를_원_단위_내림으로_다시_계산한다() {
        // API 명세서 60번 예시: 12주 @79,000 보유 중 6주 @84,500 매수 -> 18주 @80,833
        Holding holding = Holding.create(1L, 1L);
        holding.addBuy(12, 79_000);
        holding.addBuy(6, 84_500);

        assertThat(holding.getQuantity()).isEqualTo(18);
        assertThat(holding.getAvgPrice()).isEqualTo(80_833);
    }

    @Test
    void 전부_매도하면_평균_단가가_0이_된다() {
        Holding holding = Holding.create(1L, 1L);
        holding.addBuy(5, 10_000);
        holding.subtractSell(5);

        assertThat(holding.isEmpty()).isTrue();
        assertThat(holding.getAvgPrice()).isZero();
    }

    @Test
    void 체결_금액은_수량_곱하기_가격이다() {
        TradeOrder order = TradeOrder.createBuy(1L, 1L, 5, 6, 84_500, 1L);

        assertThat(order.isBuy()).isTrue();
        assertThat(order.getAmount()).isEqualTo(507_000L);
    }
}
