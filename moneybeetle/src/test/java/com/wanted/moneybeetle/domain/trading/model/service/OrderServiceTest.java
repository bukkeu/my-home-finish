package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SectorRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeReasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.MyOrderListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.OrderRequestDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.OrderResultDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.Sector;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#25)
*   OrderService 확인 (DB 없이 Repository 는 가짜(mock))
*   - 매수·매도 : 그 턴 고정 가격으로 체결, 현금·보유·거래 내역이 함께 바뀐다
*   - 거절 : 현금·수량 부족, 매매 이유 없음, 이전 턴 가격, 휴장·종료·정산 중, 참가하지 않음 - 거절되면 아무것도 바뀌지 않는다
*   - 매매 이유 선택지, 내 거래 내역
*  */
class OrderServiceTest {

    private static final long SEASON_ID = 7L;
    private static final long MEMBER_ID = 8L;
    private static final long STOCK_ID = 1L;
    private static final long REASON_ID = 4L;
    private static final int PRICE = 100_000;

    private TradingSeasonRepository seasonRepository;
    private SeasonParticipantRepository participantRepository;
    private SectorRepository sectorRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private HoldingRepository holdingRepository;
    private TradeOrderRepository orderRepository;
    private TradeReasonRepository reasonRepository;
    private OrderService service;
    private TradingSeason season;
    private SeasonParticipant participant;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        sectorRepository = mock(SectorRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        holdingRepository = mock(HoldingRepository.class);
        orderRepository = mock(TradeOrderRepository.class);
        reasonRepository = mock(TradeReasonRepository.class);
        service = new OrderService(seasonRepository, participantRepository, sectorRepository, stockRepository,
                priceRepository, holdingRepository, orderRepository, reasonRepository, clockAt("2026-11-05T12:00:00"));

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        for (int i = 0; i < 5; i++) {
            season.advanceTurn();
        }
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.of(season));
        when(seasonRepository.findSeasonIdsByStatusIn(anyList(), any(Pageable.class))).thenReturn(List.of(SEASON_ID));
        when(seasonRepository.findByIdForShare(SEASON_ID)).thenReturn(Optional.of(season));
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));

        participant = SeasonParticipant.create(SEASON_ID, MEMBER_ID, 1_000_000, 0);
        ReflectionTestUtils.setField(participant, "seasonParticipantId", 20L);
        when(participantRepository.findForUpdateBySeasonAndMember(SEASON_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        Sector sector = Sector.create("반도체", 1);
        ReflectionTestUtils.setField(sector, "sectorId", 1L);
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(sector));
        Stock stock = Stock.create(1L, "한빛반도체", 80_000);
        ReflectionTestUtils.setField(stock, "stockId", STOCK_ID);
        when(stockRepository.findById(STOCK_ID)).thenReturn(Optional.of(stock));
        when(priceRepository.findByTradingSeasonIdAndStockIdAndTurnNo(SEASON_ID, STOCK_ID, 5))
                .thenReturn(Optional.of(StockPrice.create(SEASON_ID, STOCK_ID, 5, PRICE, new BigDecimal("1.00"))));

        TradeReason reason = TradeReason.create("뉴스를 보고", 1);
        ReflectionTestUtils.setField(reason, "tradeReasonId", REASON_ID);
        when(reasonRepository.findById(REASON_ID)).thenReturn(Optional.of(reason));
        when(orderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> {
            TradeOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "tradeOrderId", 901L);
            return order;
        });
        when(holdingRepository.save(any(Holding.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 한국 시간 그 시각에 멈춘 시계 */
    private static java.time.Clock clockAt(String localDateTime) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        return java.time.Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone);
    }

    private OrderRequestDTO order(String type, int quantity, int turnNo, Long reasonId) {
        return new OrderRequestDTO(STOCK_ID, type, quantity, turnNo, reasonId);
    }

    private Holding givenHolding(int quantity, int avgPrice) {
        Holding holding = Holding.create(20L, STOCK_ID);
        holding.addBuy(quantity, avgPrice);
        when(holdingRepository.findBySeasonParticipantIdAndStockId(20L, STOCK_ID)).thenReturn(Optional.of(holding));
        return holding;
    }

    private void assertRejected(OrderRequestDTO request, ErrorCode expected) {
        long cashBefore = participant.getCash();
        assertThatThrownBy(() -> service.placeOrder(MEMBER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        assertThat(participant.getCash()).isEqualTo(cashBefore);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 매수는_그_턴_가격으로_체결하고_현금_보유_거래_내역을_함께_바꾼다() {
        Holding existing = givenHolding(12, 80_000);

        OrderResultDTO result = service.placeOrder(MEMBER_ID, order("BUY", 6, 5, REASON_ID));

        assertThat(result.orderId()).isEqualTo(901L);
        assertThat(result.price()).isEqualTo(PRICE);
        assertThat(result.amount()).isEqualTo(600_000);
        assertThat(result.cash()).isEqualTo(400_000);
        assertThat(participant.getCash()).isEqualTo(400_000);
        // (12 x 80,000 + 6 x 100,000) ÷ 18 = 86,666 (원 단위 내림)
        assertThat(result.holding()).isEqualTo(new OrderResultDTO.Holding(STOCK_ID, 18, 86_666));
        assertThat(existing.getQuantity()).isEqualTo(18);

        ArgumentCaptor<TradeOrder> saved = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderType()).isEqualTo("BUY");
        assertThat(saved.getValue().getTurnNo()).isEqualTo(5);
        assertThat(saved.getValue().getTradeReasonId()).isEqualTo(REASON_ID);
        assertThat(saved.getValue().getAmount()).isEqualTo(600_000);
    }

    @Test
    void 처음_사는_종목은_보유_기록을_새로_만든다() {
        when(holdingRepository.findBySeasonParticipantIdAndStockId(20L, STOCK_ID)).thenReturn(Optional.empty());

        OrderResultDTO result = service.placeOrder(MEMBER_ID, order("BUY", 3, 5, REASON_ID));

        verify(holdingRepository).save(any(Holding.class));
        assertThat(result.holding()).isEqualTo(new OrderResultDTO.Holding(STOCK_ID, 3, PRICE));
        assertThat(result.cash()).isEqualTo(700_000);
    }

    @Test
    void 매도는_현금이_늘고_보유가_줄고_다_팔면_평균_단가가_0이다() {
        Holding holding = givenHolding(5, 90_000);

        OrderResultDTO partial = service.placeOrder(MEMBER_ID, order("SELL", 2, 5, REASON_ID));

        assertThat(partial.amount()).isEqualTo(200_000);
        assertThat(partial.cash()).isEqualTo(1_200_000);
        assertThat(partial.holding()).isEqualTo(new OrderResultDTO.Holding(STOCK_ID, 3, 90_000));

        OrderResultDTO rest = service.placeOrder(MEMBER_ID, order("SELL", 3, 5, REASON_ID));

        assertThat(rest.holding()).isEqualTo(new OrderResultDTO.Holding(STOCK_ID, 0, 0));
        assertThat(holding.isEmpty()).isTrue();
    }

    @Test
    void 현금이_모자라면_NOT_ENOUGH_CASH_이고_아무것도_바뀌지_않는다() {
        Holding holding = givenHolding(1, 90_000);

        assertRejected(order("BUY", 11, 5, REASON_ID), TradingErrorCode.NOT_ENOUGH_CASH);   // 1,100,000 > 1,000,000
        assertThat(holding.getQuantity()).isEqualTo(1);
    }

    @Test
    void 보유보다_많이_팔거나_없는_종목을_팔면_NOT_ENOUGH_QUANTITY() {
        givenHolding(3, 90_000);
        assertRejected(order("SELL", 4, 5, REASON_ID), TradingErrorCode.NOT_ENOUGH_QUANTITY);

        when(holdingRepository.findBySeasonParticipantIdAndStockId(20L, STOCK_ID)).thenReturn(Optional.empty());
        assertRejected(order("SELL", 1, 5, REASON_ID), TradingErrorCode.NOT_ENOUGH_QUANTITY);
    }

    @Test
    void 매매_이유가_없거나_없는_이유면_REASON_REQUIRED() {
        assertRejected(order("BUY", 1, 5, null), TradingErrorCode.REASON_REQUIRED);
        assertRejected(order("BUY", 1, 5, 99L), TradingErrorCode.REASON_REQUIRED);
    }

    @Test
    void 사용_안_하는_매매_이유는_REASON_REQUIRED() {
        TradeReason inactive = TradeReason.create("옛 선택지", 9);
        ReflectionTestUtils.setField(inactive, "tradeReasonId", 9L);
        ReflectionTestUtils.setField(inactive, "isActive", false);
        when(reasonRepository.findById(9L)).thenReturn(Optional.of(inactive));

        assertRejected(order("BUY", 1, 5, 9L), TradingErrorCode.REASON_REQUIRED);
    }

    @Test
    void 화면이_본_턴이_현재_턴과_다르면_STALE_TURN_PRICE() {
        assertRejected(order("BUY", 1, 4, REASON_ID), TradingErrorCode.STALE_TURN_PRICE);
        assertRejected(order("BUY", 1, 6, REASON_ID), TradingErrorCode.STALE_TURN_PRICE);
    }

    @Test
    void 휴장_중이면_MARKET_HOLIDAY() {
        season.changeStatus(TradingSeason.STATUS_HOLIDAY);

        assertRejected(order("BUY", 1, 5, REASON_ID), TradingErrorCode.MARKET_HOLIDAY);
    }

    @Test
    void 정산_중이거나_종료된_시즌은_SEASON_CLOSED() {
        season.changeStatus(TradingSeason.STATUS_SETTLING);

        assertRejected(order("BUY", 1, 5, REASON_ID), TradingErrorCode.SEASON_CLOSED);
    }

    @Test
    void 첫_턴이_시작되기_전이거나_보이는_시즌이_없으면_NO_RUNNING_SEASON() {
        when(seasonRepository.findSeasonIdsByStatusIn(anyList(), any(Pageable.class))).thenReturn(List.of());

        assertRejected(order("BUY", 1, 5, REASON_ID), TradingErrorCode.NO_RUNNING_SEASON);

        TradingSeason notStarted = TradingSeason.create("시즌 8", LocalDate.of(2026, 11, 16), 1L);
        ReflectionTestUtils.setField(notStarted, "tradingSeasonId", 8L);
        notStarted.changeStatus(TradingSeason.STATUS_RUNNING);
        when(seasonRepository.findSeasonIdsByStatusIn(anyList(), any(Pageable.class))).thenReturn(List.of(8L));
        when(seasonRepository.findByIdForShare(8L)).thenReturn(Optional.of(notStarted));

        assertRejected(order("BUY", 1, 0, REASON_ID), TradingErrorCode.NO_RUNNING_SEASON);
    }

    @Test
    void 주문은_시즌_행을_공유_잠금으로_읽은_뒤_상태를_확인하고_참가자_행을_잠근다() {
        // 번호만 읽은 뒤(객체를 올리지 않음) 잠금 읽기로 최신 상태를 본다. 그 사이 시즌이 정산 중으로 바뀌었다면 거절한다
        season.changeStatus(TradingSeason.STATUS_SETTLING);

        assertRejected(order("BUY", 1, 5, REASON_ID), TradingErrorCode.SEASON_CLOSED);
        verify(participantRepository, never()).findForUpdateBySeasonAndMember(any(), any());
    }

    @Test
    void 정상_주문은_시즌_번호_읽기_시즌_공유_잠금_참가자_잠금_순서로_처리한다() {
        service.placeOrder(MEMBER_ID, order("BUY", 1, 5, REASON_ID));

        InOrder calls = inOrder(seasonRepository, participantRepository);
        calls.verify(seasonRepository).findSeasonIdsByStatusIn(anyList(), any(Pageable.class));
        calls.verify(seasonRepository).findByIdForShare(SEASON_ID);
        calls.verify(participantRepository).findForUpdateBySeasonAndMember(SEASON_ID, MEMBER_ID);
        verify(seasonRepository, never()).findFirstByStatusInOrderByStartDateDesc(anyList());
    }

    @Test
    void 참가하지_않았으면_NOT_JOINED() {
        when(participantRepository.findForUpdateBySeasonAndMember(SEASON_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertRejected(order("BUY", 1, 5, REASON_ID), TradingErrorCode.NOT_JOINED);
    }

    @Test
    void 사용_안_하는_종목_분야나_없는_종목은_NOT_FOUND() {
        Stock inactive = Stock.create(1L, "정리된 종목", 10_000);
        ReflectionTestUtils.setField(inactive, "stockId", 2L);
        inactive.deactivate();
        when(stockRepository.findById(2L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.placeOrder(MEMBER_ID,
                new OrderRequestDTO(2L, "BUY", 1, 5, REASON_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));

        Sector inactiveSector = Sector.create("정리된 분야", 2);
        ReflectionTestUtils.setField(inactiveSector, "isActive", false);
        when(sectorRepository.findById(1L)).thenReturn(Optional.of(inactiveSector));
        assertRejected(order("BUY", 1, 5, REASON_ID), CommonErrorCode.NOT_FOUND);

        assertThatThrownBy(() -> service.placeOrder(MEMBER_ID, new OrderRequestDTO(99L, "BUY", 1, 5, REASON_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 로그인하지_않았으면_UNAUTHORIZED() {
        assertThatThrownBy(() -> service.placeOrder(null, order("BUY", 1, 5, REASON_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.findMyOrders(null, null, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    void 매매_이유_선택지는_사용_중인_것만_정렬_순서대로_준다() {
        TradeReason a = TradeReason.create("뉴스를 보고", 1);
        ReflectionTestUtils.setField(a, "tradeReasonId", 1L);
        TradeReason b = TradeReason.create("나눠 담으려고", 2);
        ReflectionTestUtils.setField(b, "tradeReasonId", 2L);
        when(reasonRepository.findByIsActiveTrueOrderBySortOrderAscTradeReasonIdAsc()).thenReturn(List.of(a, b));

        assertThat(service.findTradeReasons()).extracting("reasonId", "label")
                .containsExactly(tuple(1L, "뉴스를 보고"),
                        tuple(2L, "나눠 담으려고"));
    }

    @Test
    void 내_거래_내역은_최근_거래부터_종목과_매매_이유_이름과_함께_준다() {
        TradeOrder buy = TradeOrder.createBuy(20L, STOCK_ID, 5, 6, 84_500, REASON_ID);
        ReflectionTestUtils.setField(buy, "tradeOrderId", 901L);
        TradeOrder sell = TradeOrder.createSell(20L, STOCK_ID, 6, 2, 90_000, null);
        ReflectionTestUtils.setField(sell, "tradeOrderId", 902L);
        when(orderRepository.findBySeasonParticipantId(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sell, buy), PageRequest.of(0, 20), 2));
        Stock stock = Stock.create(1L, "한빛반도체", 80_000);
        ReflectionTestUtils.setField(stock, "stockId", STOCK_ID);
        when(stockRepository.findAllById(any())).thenReturn(List.of(stock));
        TradeReason reason = TradeReason.create("뉴스를 보고", 1);
        ReflectionTestUtils.setField(reason, "tradeReasonId", REASON_ID);
        when(reasonRepository.findAllById(any())).thenReturn(List.of(reason));

        MyOrderListDTO list = service.findMyOrders(MEMBER_ID, null, 0, 20);

        assertThat(list.totalCount()).isEqualTo(2);
        assertThat(list.items()).extracting(MyOrderListDTO.Item::orderId).containsExactly(902L, 901L);
        assertThat(list.items().get(0).reason()).isNull();
        assertThat(list.items().get(1).reason()).isEqualTo("뉴스를 보고");
        assertThat(list.items().get(1).stockName()).isEqualTo("한빛반도체");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findBySeasonParticipantId(eq(20L), pageable.capture());
        assertThat(pageable.getValue().getSort().toString()).startsWith("tradeOrderId: DESC");
    }

    @Test
    void 참가하지_않은_시즌의_거래_내역은_비어_있다() {
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, MEMBER_ID)).thenReturn(Optional.empty());

        MyOrderListDTO list = service.findMyOrders(MEMBER_ID, SEASON_ID, 0, 20);

        assertThat(list.totalCount()).isZero();
        assertThat(list.items()).isEmpty();
    }

    @Test
    void 없는_시즌과_잘못된_쪽_크기는_거절한다() {
        assertThatThrownBy(() -> service.findMyOrders(MEMBER_ID, 99L, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        for (int[] bad : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> service.findMyOrders(MEMBER_ID, null, bad[0], bad[1]))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
    }

    // ===== #208 정규장 시간 08:00~20:00 =====

    private OrderService serviceAt(String localDateTime) {
        return new OrderService(seasonRepository, participantRepository, sectorRepository, stockRepository,
                priceRepository, holdingRepository, orderRepository, reasonRepository, clockAt(localDateTime));
    }

    @Test
    void 장_시간_안_경계_08시_정각과_19시_59분_59초에는_주문이_체결된다() {
        when(holdingRepository.findBySeasonParticipantIdAndStockId(20L, STOCK_ID)).thenReturn(Optional.empty());

        assertThat(serviceAt("2026-11-05T08:00:00").placeOrder(MEMBER_ID, order("BUY", 1, 5, REASON_ID)).amount())
                .isEqualTo(PRICE);
        assertThat(serviceAt("2026-11-05T19:59:59").placeOrder(MEMBER_ID, order("BUY", 1, 5, REASON_ID)).amount())
                .isEqualTo(PRICE);
    }

    @Test
    void 장_시간_밖_07시_59분_59초와_20시_정각과_한밤중에는_MARKET_CLOSED_이고_아무것도_바뀌지_않는다() {
        for (String time : new String[] {"2026-11-05T07:59:59", "2026-11-05T20:00:00", "2026-11-05T00:00:00",
                "2026-11-05T23:59:59"}) {
            long cashBefore = participant.getCash();
            assertThatThrownBy(() -> serviceAt(time).placeOrder(MEMBER_ID, order("BUY", 1, 5, REASON_ID)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOSED));
            assertThat(participant.getCash()).isEqualTo(cashBefore);
        }
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 장이_닫혀_있으면_매도도_거절한다() {
        givenHolding(3, 90_000);

        assertThatThrownBy(() -> serviceAt("2026-11-05T21:00:00").placeOrder(MEMBER_ID, order("SELL", 1, 5, REASON_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOSED));
    }

    @Test
    void 일요일에는_상태가_진행_중이어도_장_시간_안_주문을_MARKET_HOLIDAY_로_거절한다() {
        // 2026-11-08 은 일요일. 관리자가 상태를 휴장으로 바꾸지 않았어도 거래되지 않는다
        long cashBefore = participant.getCash();
        assertThatThrownBy(() -> serviceAt("2026-11-08T12:00:00").placeOrder(MEMBER_ID, order("BUY", 1, 5, REASON_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_HOLIDAY));
        assertThat(participant.getCash()).isEqualTo(cashBefore);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 휴장과_정산_중_거절이_장_시간_거절보다_먼저다() {
        season.changeStatus(TradingSeason.STATUS_HOLIDAY);
        assertThatThrownBy(() -> serviceAt("2026-11-05T03:00:00").placeOrder(MEMBER_ID, order("BUY", 1, 5, REASON_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_HOLIDAY));
    }
}
