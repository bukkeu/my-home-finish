package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#45)
*   PortfolioValuer 확인 : 순자산 = 현금 + 보유 평가금액(현재 턴 가격), 수익률(SP-61), 순위(동점은 같은 순위)
*  */
class PortfolioValuerTest {

    private static final long SEASON_ID = 7L;

    private HoldingRepository holdingRepository;
    private StockPriceRepository priceRepository;
    private PortfolioValuer valuer;
    private TradingSeason season;

    @BeforeEach
    void setUp() {
        holdingRepository = mock(HoldingRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        valuer = new PortfolioValuer(holdingRepository, priceRepository);

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        for (int i = 0; i < 5; i++) {
            season.advanceTurn();
        }
    }

    private SeasonParticipant participant(Long id, long investAmount, long cash) {
        SeasonParticipant participant = SeasonParticipant.create(SEASON_ID, id * 10, investAmount, 0);
        ReflectionTestUtils.setField(participant, "seasonParticipantId", id);
        ReflectionTestUtils.setField(participant, "cash", cash);
        return participant;
    }

    private Holding holding(Long participantId, Long stockId, int quantity, int price) {
        Holding holding = Holding.create(participantId, stockId);
        holding.addBuy(quantity, price);
        return holding;
    }

    private void givenPricesAtTurn5(Map<Long, Integer> prices) {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 5)).thenReturn(prices.entrySet().stream()
                .map(e -> StockPrice.create(SEASON_ID, e.getKey(), 5, e.getValue(), new BigDecimal("1.00")))
                .toList());
    }

    @Test
    void 순자산은_현금과_보유_평가금액의_합이고_수익률은_투자_가능_금액_대비다() {
        SeasonParticipant a = participant(1L, 1_000_000, 400_000);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L)))
                .thenReturn(List.of(holding(1L, 1L, 6, 80_000), holding(1L, 2L, 2, 50_000)));
        givenPricesAtTurn5(Map.of(1L, 100_000, 2L, 40_000));

        PortfolioValuer.Valuation valuation = valuer.value(season, List.of(a)).get(1L);

        // 400,000 + 6 x 100,000 + 2 x 40,000
        assertThat(valuation.netAsset()).isEqualTo(1_080_000);
        assertThat(valuation.returnRate()).isEqualByComparingTo("8.00");
    }

    @Test
    void 가격이_없는_종목은_평균_매수_단가로_평가한다() {
        SeasonParticipant a = participant(1L, 1_000_000, 500_000);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L)))
                .thenReturn(List.of(holding(1L, 9L, 5, 100_000)));
        givenPricesAtTurn5(Map.of());

        assertThat(valuer.value(season, List.of(a)).get(1L).netAsset()).isEqualTo(1_000_000);
    }

    @Test
    void 수량이_0인_보유_기록은_평가에_넣지_않는다() {
        SeasonParticipant a = participant(1L, 1_000_000, 700_000);
        Holding sold = holding(1L, 1L, 3, 100_000);
        sold.subtractSell(3);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L))).thenReturn(List.of(sold));
        givenPricesAtTurn5(Map.of(1L, 100_000));

        assertThat(valuer.value(season, List.of(a)).get(1L).netAsset()).isEqualTo(700_000);
    }

    @Test
    void 정산된_참가자는_확정한_값을_그대로_쓴다() {
        SeasonParticipant a = participant(1L, 1_000_000, 100);
        a.settle(1_234_567, new BigDecimal("23.46"), 1_234_567);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L))).thenReturn(List.of());
        givenPricesAtTurn5(Map.of());

        PortfolioValuer.Valuation valuation = valuer.value(season, List.of(a)).get(1L);

        assertThat(valuation.netAsset()).isEqualTo(1_234_567);
        assertThat(valuation.returnRate()).isEqualByComparingTo("23.46");
    }

    @Test
    void 턴이_시작되기_전에는_가격을_읽지_않고_현금이_순자산이다() {
        TradingSeason notStarted = TradingSeason.create("시즌 8", LocalDate.of(2026, 11, 16), 1L);
        ReflectionTestUtils.setField(notStarted, "tradingSeasonId", 8L);
        SeasonParticipant a = participant(1L, 1_000_000, 1_000_000);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L))).thenReturn(List.of());

        assertThat(valuer.value(notStarted, List.of(a)).get(1L).netAsset()).isEqualTo(1_000_000);
        assertThat(valuer.pricesAtValuationTurn(notStarted)).isEmpty();
    }

    @Test
    void 참가자가_없으면_아무것도_읽지_않는다() {
        assertThat(valuer.value(season, List.of())).isEmpty();
    }

    @Test
    void 수익률은_소수_둘째_자리까지_반올림하고_투자_금액이_0이면_0이다() {
        assertThat(PortfolioValuer.returnRate(1_000_001, 3_000_000)).isEqualByComparingTo("-66.67");
        assertThat(PortfolioValuer.returnRate(1_000_000, 3_000_000)).isEqualByComparingTo("-66.67");
        assertThat(PortfolioValuer.returnRate(1_050_000, 1_000_000)).isEqualByComparingTo("5.00");
        assertThat(PortfolioValuer.returnRate(500, 0)).isEqualByComparingTo("0.00");
    }

    @Test
    void 순위는_큰_값부터_매기고_동점은_같은_순위로_다음_순위를_건너뛴다() {
        List<long[]> rows = List.of(new long[] {1, 500}, new long[] {2, 900}, new long[] {3, 500}, new long[] {4, 900},
                new long[] {5, 100});

        List<PortfolioValuer.Ranked<long[]>> ranked = PortfolioValuer.rank(rows, row -> row[1]);

        assertThat(ranked).extracting(PortfolioValuer.Ranked::rank).containsExactly(1, 1, 3, 3, 5);
        // 같은 순위 안에서는 입력 순서를 지킨다
        assertThat(ranked).extracting(r -> r.item()[0]).containsExactly(2L, 4L, 1L, 3L, 5L);
    }

    @Test
    void 수익률로도_순위를_매길_수_있다() {
        List<BigDecimal> rates = List.of(new BigDecimal("5.00"), new BigDecimal("5.0"), new BigDecimal("-1.20"));

        List<PortfolioValuer.Ranked<BigDecimal>> ranked = PortfolioValuer.rank(rates, rate -> rate);

        assertThat(ranked).extracting(PortfolioValuer.Ranked::rank).containsExactly(1, 1, 3);
    }
}
