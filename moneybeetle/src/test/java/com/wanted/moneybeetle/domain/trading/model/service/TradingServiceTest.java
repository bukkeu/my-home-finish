package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.NewsTagRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.NewsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SectorStocksDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.StockListDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.News;
import com.wanted.moneybeetle.domain.trading.model.entity.NewsTag;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#24)
*   TradingService 시세·뉴스 조회 확인 (DB 없이 Repository 는 가짜(mock))
*   - 진행 중·휴장 중·정산 중·종료 시즌이 하나도 없으면 NO_RUNNING_SEASON, 정산 중 시즌도 시세·뉴스를 보여 준다
*   - 현재 턴까지만 보이고(가격 흐름·뉴스), 관리자 메모는 응답에 없다
*   - 시세 정렬(분야 순서·인기순)과 거르기, 내 보유
*  */
class TradingServiceTest {

    private static final long SEASON_ID = 7L;

    private TradingSeasonRepository seasonRepository;
    private SectorRepository sectorRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private NewsRepository newsRepository;
    private NewsTagRepository newsTagRepository;
    private SeasonParticipantRepository participantRepository;
    private HoldingRepository holdingRepository;
    private TradeOrderRepository orderRepository;
    private TradingService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        sectorRepository = mock(SectorRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        newsRepository = mock(NewsRepository.class);
        newsTagRepository = mock(NewsTagRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        holdingRepository = mock(HoldingRepository.class);
        orderRepository = mock(TradeOrderRepository.class);
        service = new TradingService(seasonRepository, sectorRepository, stockRepository, priceRepository,
                newsRepository, newsTagRepository, participantRepository, holdingRepository, orderRepository);
    }

    /** 5턴 진행 중인 시즌 7 */
    private void givenRunningSeasonAtTurn5() {
        TradingSeason season = TradingSeason.create("시즌 7", LocalDate.of(2026, 10, 19), new BigDecimal("100.00"),
                null, 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.start();
        for (int i = 0; i < 5; i++) {
            season.advanceTurn();
        }
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.of(season));
    }

    private Sector sector(long id, String name, int sortOrder) {
        Sector sector = Sector.create(name, sortOrder);
        ReflectionTestUtils.setField(sector, "sectorId", id);
        return sector;
    }

    private Stock stock(long id, long sectorId, String name) {
        Stock stock = Stock.create(sectorId, name, 10_000);
        ReflectionTestUtils.setField(stock, "stockId", id);
        return stock;
    }

    private News news(long id, int turnNo, String title) {
        News news = News.create(SEASON_ID, turnNo, title, title + " 본문", "관리자만 보는 메모");
        ReflectionTestUtils.setField(news, "newsId", id);
        return news;
    }

    /** 분야 2개(모빌리티가 정렬 1번), 종목 3개 */
    private void givenMarket() {
        when(sectorRepository.findByIsActiveTrueOrderBySortOrderAscSectorIdAsc())
                .thenReturn(List.of(sector(2L, "모빌리티", 1), sector(1L, "반도체", 2)));
        when(stockRepository.findByIsActiveTrueOrderByStockIdAsc())
                .thenReturn(List.of(stock(1L, 1L, "한빛반도체"), stock(2L, 2L, "모아모빌리티"), stock(8L, 1L, "미래칩스")));
    }

    @Test
    void 진행_중이거나_휴장_중인_시즌이_없으면_NO_RUNNING_SEASON() {
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findStocks(null, null, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.NO_RUNNING_SEASON);
        verify(seasonRepository).findFirstByStatusInOrderByStartDateDesc(
                List.of(TradingSeason.STATUS_RUNNING, TradingSeason.STATUS_HOLIDAY,
                        TradingSeason.STATUS_SETTLING, TradingSeason.STATUS_ENDED));
    }

    @Test
    void 정산_중인_시즌도_마지막_턴의_시세와_뉴스를_보여준다() {
        TradingSeason season = TradingSeason.create("시즌 7", LocalDate.of(2026, 10, 19), new BigDecimal("100.00"),
                null, 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.start();
        for (int i = 0; i < 12; i++) {
            season.advanceTurn();
        }
        season.startSettling();
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.of(season));
        givenMarket();
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 12)).thenReturn(List.of(
                StockPrice.create(SEASON_ID, 1L, 12, 96_950, new BigDecimal("4.98"))));
        when(newsRepository.findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(SEASON_ID, 12))
                .thenReturn(List.of(news(9L, 12, "정산 중 뉴스")));

        StockListDTO stocks = service.findStocks(null, null, null);
        assertThat(stocks.getTurnNo()).isEqualTo(12);
        assertThat(stocks.getStocks()).extracting(StockDTO::getPrice).contains(96_950);
        assertThat(service.findNews(null, null)).extracting(NewsDTO::getTitle).containsExactly("정산 중 뉴스");
        verify(priceRepository).findByTradingSeasonIdAndTurnNo(SEASON_ID, 12);
    }

    @Test
    void 분야_목록은_정렬_순서대로_소속_종목을_붙인다() {
        givenMarket();

        List<SectorStocksDTO> sectors = service.findSectors();

        assertThat(sectors).extracting(SectorStocksDTO::getName).containsExactly("모빌리티", "반도체");
        assertThat(sectors.get(1).getStocks()).extracting(SectorStocksDTO.StockName::getName)
                .containsExactly("한빛반도체", "미래칩스");
    }

    @Test
    void 시세는_현재_턴_가격을_분야_순서대로_보여준다() {
        givenRunningSeasonAtTurn5();
        givenMarket();
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 5)).thenReturn(List.of(
                StockPrice.create(SEASON_ID, 1L, 5, 84_500, new BigDecimal("2.80")),
                StockPrice.create(SEASON_ID, 2L, 5, 32_800, new BigDecimal("1.39"))));

        StockListDTO result = service.findStocks(null, null, null);

        assertThat(result.getTurnNo()).isEqualTo(5);
        assertThat(result.getStocks()).extracting(StockDTO::getName)
                .containsExactly("모아모빌리티", "한빛반도체", "미래칩스");
        assertThat(result.getStocks().get(1).getPrice()).isEqualTo(84_500);
        assertThat(result.getStocks().get(1).getChangeRate()).isEqualByComparingTo("2.80");
        // 가격이 아직 없는 종목은 null 로 보인다
        assertThat(result.getStocks().get(2).getPrice()).isNull();
        verify(orderRepository, never()).countOrdersByStock(any());
    }

    @Test
    void 인기순은_이번_시즌_거래_수가_많은_순이고_같으면_종목_번호_순() {
        givenRunningSeasonAtTurn5();
        givenMarket();
        when(orderRepository.countOrdersByStock(SEASON_ID)).thenReturn(List.of(count(8L, 5), count(2L, 5), count(1L, 1)));

        StockListDTO result = service.findStocks(TradingService.SORT_POPULAR, null, null);

        assertThat(result.getStocks()).extracting(StockDTO::getStockId).containsExactly(2L, 8L, 1L);
    }

    @Test
    void 시세는_분야와_이름_일부로_거를_수_있다() {
        givenRunningSeasonAtTurn5();
        givenMarket();

        assertThat(service.findStocks(null, 1L, null).getStocks()).extracting(StockDTO::getName)
                .containsExactly("한빛반도체", "미래칩스");
        assertThat(service.findStocks(null, null, " 칩 ").getStocks()).extracting(StockDTO::getName)
                .containsExactly("미래칩스");
    }

    @Test
    void 종목_상세는_현재_턴까지의_가격_흐름과_기업정보를_보여준다() {
        givenRunningSeasonAtTurn5();
        Stock stock = stock(1L, 1L, "한빛반도체");
        stock.changeCompanyInfo("소개", "메모리", "1조 2,000억", "1,450억");
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        when(priceRepository.findByTradingSeasonIdAndStockIdAndTurnNoLessThanEqualOrderByTurnNoAsc(SEASON_ID, 1L, 5))
                .thenReturn(List.of(StockPrice.create(SEASON_ID, 1L, 1, 92_900, null),
                        StockPrice.create(SEASON_ID, 1L, 5, 84_500, new BigDecimal("2.80"))));

        StockDetailDTO detail = service.getStock(1L, null);

        assertThat(detail.getSectorName()).isEqualTo("반도체");
        assertThat(detail.getPrice()).isEqualTo(84_500);
        assertThat(detail.getPriceHistory()).extracting(StockDetailDTO.PricePoint::getTurnNo).containsExactly(1, 5);
        assertThat(detail.getCompany().getBusiness()).isEqualTo("메모리");
        assertThat(detail.getMyHolding()).isNull();
        // 현재 턴(5)까지만 조회한다 -> 미리 입력된 6턴 이후 가격은 나오지 않는다
        verify(priceRepository).findByTradingSeasonIdAndStockIdAndTurnNoLessThanEqualOrderByTurnNoAsc(SEASON_ID, 1L, 5);
        verify(participantRepository, never()).findByTradingSeasonIdAndMemberId(any(), any());
    }

    @Test
    void 종목_상세의_내_보유는_참가한_시즌의_보유_수량과_평균_단가() {
        givenRunningSeasonAtTurn5();
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 1L, "한빛반도체")));
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector(1L, "반도체", 1)));
        SeasonParticipant participant = SeasonParticipant.create(SEASON_ID, 8L, 3_000_000L, 0L);
        ReflectionTestUtils.setField(participant, "seasonParticipantId", 120L);
        Holding holding = Holding.create(120L, 1L);
        holding.addBuy(12, 79_000);
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, 8L)).thenReturn(Optional.of(participant));
        when(holdingRepository.findBySeasonParticipantIdAndStockId(120L, 1L)).thenReturn(Optional.of(holding));
        when(priceRepository.findByTradingSeasonIdAndStockIdAndTurnNoLessThanEqualOrderByTurnNoAsc(
                eq(SEASON_ID), eq(1L), anyInt())).thenReturn(List.of());

        StockDetailDTO detail = service.getStock(1L, 8L);

        assertThat(detail.getMyHolding().getQuantity()).isEqualTo(12);
        assertThat(detail.getMyHolding().getAvgPrice()).isEqualTo(79_000);
        assertThat(detail.getPrice()).isNull();
    }

    @Test
    void 사용_안_함_분야의_종목_상세는_NOT_FOUND() {
        givenRunningSeasonAtTurn5();
        Sector inactive = sector(3L, "옛분야", 9);
        inactive.deactivate();
        when(stockRepository.findById(5L)).thenReturn(Optional.of(stock(5L, 3L, "옛분야종목")));
        when(sectorRepository.findById(3L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getStock(5L, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 없거나_사용_안_함인_종목은_NOT_FOUND() {
        givenRunningSeasonAtTurn5();
        Stock inactive = stock(3L, 1L, "옛종목");
        inactive.deactivate();
        when(stockRepository.findById(3L)).thenReturn(Optional.of(inactive));
        when(stockRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStock(3L, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.getStock(99L, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 뉴스는_현재_턴까지만_관련_분야_종목_이름과_함께_보여준다() {
        givenRunningSeasonAtTurn5();
        when(newsRepository.findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(SEASON_ID, 5))
                .thenReturn(List.of(news(9L, 5, "AI 기기 수요 확대"), news(5L, 3, "원재료 가격 급등")));
        when(newsTagRepository.findByNewsIdIn(List.of(9L, 5L))).thenReturn(List.of(
                NewsTag.createForSector(9L, 1L), NewsTag.createForStock(9L, 1L), NewsTag.createForSector(5L, 2L)));
        when(sectorRepository.findAllById(anyList())).thenReturn(List.of(sector(1L, "반도체", 1), sector(2L, "모빌리티", 2)));
        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(stock(1L, 1L, "한빛반도체")));

        List<NewsDTO> items = service.findNews(null, null);

        assertThat(items).extracting(NewsDTO::getTitle).containsExactly("AI 기기 수요 확대", "원재료 가격 급등");
        assertThat(items.get(0).getSectors()).containsExactly("반도체");
        assertThat(items.get(0).getStocks()).containsExactly("한빛반도체");
        // 현재 턴(5)까지만 조회한다
        verify(newsRepository).findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(SEASON_ID, 5);
    }

    @Test
    void 뉴스는_턴과_종목으로_거르고_현재_턴_뒤의_턴은_빈_목록() {
        givenRunningSeasonAtTurn5();
        when(newsRepository.findByTradingSeasonIdAndTurnNoLessThanEqualOrderByTurnNoDescNewsIdDesc(SEASON_ID, 5))
                .thenReturn(List.of(news(9L, 5, "AI 기기 수요 확대"), news(8L, 5, "전기차 보조금"), news(5L, 3, "원재료")));
        when(newsTagRepository.findByNewsIdIn(anyList())).thenReturn(List.of(
                NewsTag.createForStock(9L, 1L), NewsTag.createForStock(8L, 2L)));

        assertThat(service.findNews(5, 1L)).extracting(NewsDTO::getNewsId).containsExactly(9L);
        assertThat(service.findNews(3, null)).extracting(NewsDTO::getNewsId).containsExactly(5L);
        assertThat(service.findNews(6, null)).isEmpty();
    }

    @Test
    void 뉴스_응답에는_관리자_메모가_없다() {
        assertThat(NewsDTO.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .doesNotContain("adminMemo");
    }

    private static TradeOrderRepository.StockOrderCount count(long stockId, long orderCount) {
        return new TradeOrderRepository.StockOrderCount() {
            @Override
            public Long getStockId() {
                return stockId;
            }

            @Override
            public long getOrderCount() {
                return orderCount;
            }
        };
    }
}
