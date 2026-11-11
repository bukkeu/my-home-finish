package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsTagRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnCheckDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingDashboardSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#44, #193)
*   TradingService 다음 턴 점검·대시보드 요약 확인 (DB 없이 Repository 는 가짜(mock))
*   - 점검 : 사용 중 종목(사용 중 분야)의 다음 턴 가격이 모두 있고 뉴스가 1개 이상이어야 ready
*   - 대시보드 요약(#193)은 점검(API 119)과 같은 계산을 쓴다 → ready 판단이 어긋나지 않는다
*  */
class TradingServiceTurnCheckTest {

    private static final long SEASON_ID = 7L;
    /** 한국 시간 2026-11-05 21:00 (장 마감 뒤) */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneId.of("Asia/Seoul"));

    private TradingSeasonRepository seasonRepository;
    private SectorRepository sectorRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private NewsRepository newsRepository;
    private TradeOrderRepository orderRepository;
    private TradingService service;
    private TradingSeason season;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        sectorRepository = mock(SectorRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        newsRepository = mock(NewsRepository.class);
        orderRepository = mock(TradeOrderRepository.class);
        service = new TradingService(seasonRepository, sectorRepository, stockRepository, priceRepository,
                newsRepository, mock(NewsTagRepository.class), mock(SeasonParticipantRepository.class),
                mock(HoldingRepository.class), orderRepository, CLOCK);

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        for (int i = 0; i < 5; i++) {
            season.advanceTurn();
        }
        // 사용 중 분야 1개(id 1)에 종목 2개, 사용 중 종목이지만 사용 안 함 분야(id 2)의 종목 1개
        Sector active = sector(1L, "IT", true);
        Sector inactive = sector(2L, "에너지", false);
        when(sectorRepository.findByIsActiveTrueOrderBySortOrderAscSectorIdAsc()).thenReturn(List.of(active));
        when(sectorRepository.findAll()).thenReturn(List.of(active, inactive));
        when(stockRepository.findByIsActiveTrueOrderByStockIdAsc()).thenReturn(List.of(
                stock(1L, 1L, "모아모빌리티"), stock(2L, 1L, "별숲엔터"), stock(3L, 2L, "푸른에너지")));
    }

    private Sector sector(Long id, String name, boolean active) {
        Sector sector = Sector.create(name, 1);
        ReflectionTestUtils.setField(sector, "sectorId", id);
        ReflectionTestUtils.setField(sector, "isActive", active);
        return sector;
    }

    private Stock stock(Long id, Long sectorId, String name) {
        Stock stock = Stock.create(sectorId, name, 10_000);
        ReflectionTestUtils.setField(stock, "stockId", id);
        return stock;
    }

    private StockPrice price(Long stockId) {
        return StockPrice.create(SEASON_ID, stockId, 6, 10_000, new BigDecimal("1.00"));
    }

    @Test
    void 사용_중_종목의_다음_턴_가격과_뉴스가_모두_있으면_ready() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(2L);

        NextTurnCheckDTO check = service.checkNextTurn(season);

        assertThat(check.ready()).isTrue();
        assertThat(check.nextTurn()).isEqualTo(6);
        assertThat(check.nextTradeDate()).isEqualTo(LocalDate.of(2026, 11, 7));   // 6턴 = 월~토 6일째(토요일)
        assertThat(check.missingPrices()).isEmpty();   // 사용 안 함 분야의 종목은 세지 않는다
        assertThat(check.newsCount()).isEqualTo(2);
        assertThat(check.isHoliday()).isFalse();
    }

    @Test
    void 가격이_빈_종목은_이름으로_알려_주고_ready_가_아니다() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(2L);

        NextTurnCheckDTO check = service.checkNextTurn(season);

        assertThat(check.ready()).isFalse();
        assertThat(check.missingPrices()).containsExactly("별숲엔터");
    }

    @Test
    void 뉴스가_없으면_ready_가_아니다() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(0L);

        assertThat(service.checkNextTurn(season).ready()).isFalse();
    }

    @Test
    void 사용_중_종목이_하나도_없으면_ready_가_아니다() {
        when(stockRepository.findByIsActiveTrueOrderByStockIdAsc()).thenReturn(List.of());
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(1L);

        assertThat(service.checkNextTurn(season).ready()).isFalse();
    }

    @Test
    void 휴장일이면_가격과_뉴스가_있어도_ready_가_아니다() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(1L);
        season.changeStatus(TradingSeason.STATUS_HOLIDAY);

        NextTurnCheckDTO check = service.checkNextTurn(season);

        assertThat(check.ready()).isFalse();
        assertThat(check.isHoliday()).isTrue();
    }

    @Test
    void 마지막_턴이면_다음_턴이_없다() {
        for (int i = season.getCurrentTurn(); i < season.getTotalTurns(); i++) {
            season.advanceTurn();
        }

        NextTurnCheckDTO check = service.checkNextTurn(season);

        assertThat(check.ready()).isFalse();
        assertThat(check.nextTurn()).isNull();
        assertThat(check.nextTradeDate()).isNull();
    }

    @Test
    void 대시보드_요약은_오늘_주문_수와_점검_결과를_준다() {
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.of(season));
        when(orderRepository.countOrdersBetween(eq(SEASON_ID), eq(LocalDateTime.of(2026, 11, 6, 0, 0)),
                eq(LocalDateTime.of(2026, 11, 7, 0, 0)))).thenReturn(37L);
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(0L);

        TradingDashboardSummaryDTO summary = service.getDashboardSummary().orElseThrow();

        assertThat(summary.seasonId()).isEqualTo(SEASON_ID);
        assertThat(summary.todayOrders()).isEqualTo(37);
        assertThat(summary.nextTurnReady()).isFalse();
        assertThat(summary.missing().prices()).isEqualTo(1);
        assertThat(summary.missing().news()).isEqualTo(1);
    }

    @Test
    void 대시보드_요약의_ready_는_다음_턴_점검과_같다() {
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.of(season));
        when(orderRepository.countOrdersBetween(any(), any(), any())).thenReturn(0L);
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(3L);

        TradingDashboardSummaryDTO summary = service.getDashboardSummary().orElseThrow();

        assertThat(summary.nextTurnReady()).isEqualTo(service.checkNextTurn(season).ready()).isTrue();
        assertThat(summary.missing().prices()).isZero();
        assertThat(summary.missing().news()).isZero();
    }

    @Test
    void 진행_중인_시즌이_없으면_요약은_비어_있다() {
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.empty());

        assertThat(service.getDashboardSummary()).isEmpty();
    }

    // ===== #207 이전 시즌 정산 =====

    private void givenAllReady() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(1L);
    }

    private TradingSeason previous(boolean settledAndEnded) {
        TradingSeason previous = TradingSeason.create("시즌 6", season.getStartDate().minusDays(14), 1L);
        previous.changeStatus(TradingSeason.STATUS_RUNNING);
        previous.changeStatus(TradingSeason.STATUS_SETTLING);
        if (settledAndEnded) {
            previous.completeSettlement(LocalDateTime.of(2026, 11, 2, 0, 10));
            previous.publishRanking(LocalDateTime.of(2026, 11, 2, 9, 0));
        } else {
            previous.completeSettlement(LocalDateTime.of(2026, 11, 2, 0, 10));   // 정산만 하고 공개 전: 아직 종료가 아니다
        }
        return previous;
    }

    @Test
    void 이전_시즌이_정산_전이면_가격과_뉴스가_있어도_ready_가_아니다() {
        givenAllReady();
        when(seasonRepository.findFirstByStartDateBeforeOrderByStartDateDesc(season.getStartDate()))
                .thenReturn(Optional.of(previous(false)));

        NextTurnCheckDTO check = service.checkNextTurn(season);

        assertThat(check.previousSeasonSettled()).isFalse();
        assertThat(check.ready()).isFalse();
        assertThat(check.missingPrices()).isEmpty();
    }

    @Test
    void 이전_시즌이_없거나_정산을_마치고_끝났으면_ready_다() {
        givenAllReady();
        when(seasonRepository.findFirstByStartDateBeforeOrderByStartDateDesc(season.getStartDate()))
                .thenReturn(Optional.empty());
        assertThat(service.checkNextTurn(season).previousSeasonSettled()).isTrue();
        assertThat(service.checkNextTurn(season).ready()).isTrue();

        when(seasonRepository.findFirstByStartDateBeforeOrderByStartDateDesc(season.getStartDate()))
                .thenReturn(Optional.of(previous(true)));
        assertThat(service.checkNextTurn(season).ready()).isTrue();
    }

    // ===== #208 장 마감 뒤에만 턴을 넘긴다 =====

    private TradingService serviceAt(String localDateTime) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        return new TradingService(seasonRepository, sectorRepository, stockRepository, priceRepository,
                newsRepository, mock(NewsTagRepository.class), mock(SeasonParticipantRepository.class),
                mock(HoldingRepository.class), orderRepository,
                Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone));
    }

    @Test
    void 장이_열려_있는_동안에는_가격과_뉴스가_있어도_ready_가_아니다() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(1L);

        for (String open : new String[] {"2026-11-06T08:00:00", "2026-11-06T12:00:00", "2026-11-06T19:59:59"}) {
            NextTurnCheckDTO check = serviceAt(open).checkNextTurn(season);
            assertThat(check.afterMarketClose()).isFalse();
            assertThat(check.ready()).isFalse();
        }
    }

    @Test
    void 장이_닫히는_20시_정각부터_다음_날_아침_8시_전까지는_ready_다() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(1L);

        for (String closed : new String[] {"2026-11-06T20:00:00", "2026-11-06T23:59:59", "2026-11-07T03:00:00",
                "2026-11-07T07:59:59"}) {
            NextTurnCheckDTO check = serviceAt(closed).checkNextTurn(season);
            assertThat(check.afterMarketClose()).isTrue();
            assertThat(check.ready()).isTrue();
        }
    }

    @Test
    void 같은_밤에_이미_넘긴_턴은_거래일_장_마감_전이라_다시_넘길_수_없다() {
        givenAllReady();

        // 5턴의 거래일은 11월 6일. 11월 5일 밤(4턴 거래일 마감 뒤)에 5턴으로 넘긴 직후와 다음 날 아침은 닫혀 있다
        for (String sameNight : new String[] {"2026-11-05T21:00:00", "2026-11-05T23:59:59", "2026-11-06T07:00:00"}) {
            NextTurnCheckDTO check = serviceAt(sameNight).checkNextTurn(season);
            assertThat(check.afterMarketClose()).as(sameNight).isFalse();
            assertThat(check.ready()).as(sameNight).isFalse();
        }
        // 5턴의 거래일(11월 6일) 장이 마감되면 다음 턴을 넘길 수 있다
        assertThat(serviceAt("2026-11-06T21:00:00").checkNextTurn(season).ready()).isTrue();
    }

    @Test
    void 거래일이_지났어도_장이_열려_있는_동안에는_넘길_수_없다() {
        givenAllReady();

        // 관리자가 11월 6일 밤에 넘기지 못한 채 11월 7일 낮이 된 경우에도 장이 열려 있는 동안은 막는다
        NextTurnCheckDTO check = serviceAt("2026-11-07T12:00:00").checkNextTurn(season);

        assertThat(check.afterMarketClose()).isFalse();
        assertThat(check.ready()).isFalse();
    }

    @Test
    void 첫_턴을_시작하는_0턴에서는_시각을_따지지_않는다() {
        TradingSeason notStarted = TradingSeason.create("시즌 8", LocalDate.of(2026, 11, 16), 1L);
        ReflectionTestUtils.setField(notStarted, "tradingSeasonId", 8L);
        notStarted.changeStatus(TradingSeason.STATUS_RUNNING);
        when(priceRepository.findByTradingSeasonIdAndTurnNo(8L, 1)).thenReturn(List.of(
                StockPrice.create(8L, 1L, 1, 10_000, new BigDecimal("0.00")),
                StockPrice.create(8L, 2L, 1, 10_000, new BigDecimal("0.00"))));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(8L, 1)).thenReturn(1L);

        NextTurnCheckDTO check = serviceAt("2026-11-16T12:00:00").checkNextTurn(notStarted);   // 장이 열려 있는 한낮

        assertThat(check.afterMarketClose()).isTrue();
        assertThat(check.ready()).isTrue();
    }

    // ===== 일요일은 시즌 상태와 상관없이 휴장일 =====

    @Test
    void 일요일에는_시즌이_진행_중으로_남아_있어도_휴장일이고_ready_가_아니다() {
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(List.of(price(1L), price(2L)));
        when(newsRepository.countByTradingSeasonIdAndTurnNo(SEASON_ID, 6)).thenReturn(1L);

        NextTurnCheckDTO check = serviceAt("2026-11-08T21:00:00").checkNextTurn(season);   // 일요일 장 마감 뒤

        assertThat(season.isRunning()).isTrue();
        assertThat(check.isHoliday()).isTrue();
        assertThat(check.ready()).isFalse();
    }
}
