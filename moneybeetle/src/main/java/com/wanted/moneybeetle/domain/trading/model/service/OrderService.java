package com.wanted.moneybeetle.domain.trading.model.service;

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
import com.wanted.moneybeetle.domain.trading.model.dto.TradeReasonDTO;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (모의투자)
*   #25 매수·매도 주문 (API 59~61, INV-09~13, INV-15, SP-53~58, DP-36, DP-40, NFR-05)
*   - 그 턴의 고정 가격으로 즉시 체결한다 (SP-53). 화면이 본 턴(turnNo)이 현재 턴과 다르면 STALE_TURN_PRICE
*   - 주문 불가 : 정산 중·종료 SEASON_CLOSED, 일요일 휴장 MARKET_HOLIDAY, 장 시간(08:00~20:00) 밖 MARKET_CLOSED, 참가하지 않았으면 NOT_JOINED (SP-58)
*   - 매수는 보유 현금 안에서만(NOT_ENOUGH_CASH), 매도는 보유 수량 안에서만(NOT_ENOUGH_QUANTITY, 공매도 불가, SP-54·55)
*   - 매매 이유는 반드시 골라야 한다 (REASON_REQUIRED, SP-57). 수수료·세금은 없다 (SP-56)
*   - 현금·보유 수량·거래 내역은 한 트랜잭션이다. 어디서 실패해도 모두 되돌려진다 (DP-40)
*   - 같은 참가자의 주문은 참가자 행을 잠그고 처리해 동시에 들어와도 현금·수량이 어긋나지 않는다
*   - 주문은 시즌 행을 공유 잠금으로 읽고(턴 진행은 배타 잠금) 상태·현재 턴을 확인한 뒤 참가자 행을 잠근다. 주문 중에 턴이 넘어가지 않는다
*   - 같은 요청 번호(Idempotency-Key)로 다시 보내면 IdempotencyFilter 가 먼저 막는다 (DUPLICATE_REQUEST)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final TradingSeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final HoldingRepository holdingRepository;
    private final TradeOrderRepository orderRepository;
    private final TradeReasonRepository reasonRepository;
    private final Clock clock;

    /** API 59 매매 이유 선택지 */
    public List<TradeReasonDTO> findTradeReasons() {
        return reasonRepository.findByIsActiveTrueOrderBySortOrderAscTradeReasonIdAsc().stream()
                .map(TradeReasonDTO::from)
                .toList();
    }

    /** API 60 매수·매도 */
    @Transactional
    public OrderResultDTO placeOrder(Long memberId, OrderRequestDTO request) {
        if (memberId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        TradeReason reason = findReason(request.reasonId());
        TradingSeason season = lockOrderableSeason(request.turnNo());
        SeasonParticipant participant = participantRepository
                .findForUpdateBySeasonAndMember(season.getTradingSeasonId(), memberId)
                .orElseThrow(() -> new BusinessException(TradingErrorCode.NOT_JOINED));
        Stock stock = getTradableStock(request.stockId());
        int price = priceRepository.findByTradingSeasonIdAndStockIdAndTurnNo(season.getTradingSeasonId(),
                        stock.getStockId(), season.getCurrentTurn())
                .map(StockPrice::getPrice)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        boolean buy = TradeOrder.TYPE_BUY.equals(request.orderType());
        Holding holding = holdingRepository
                .findForUpdateBySeasonParticipantIdAndStockId(participant.getSeasonParticipantId(), stock.getStockId())
                .orElse(null);
        long amount = (long) request.quantity() * price;
        TradeOrder order;
        if (buy) {
            if (!participant.hasEnoughCash(amount)) {
                throw new BusinessException(TradingErrorCode.NOT_ENOUGH_CASH);
            }
            if (holding == null) {
                holding = holdingRepository.save(Holding.create(participant.getSeasonParticipantId(),
                        stock.getStockId()));
            }
            participant.payCash(amount);
            holding.addBuy(request.quantity(), price);
            order = TradeOrder.createBuy(participant.getSeasonParticipantId(), stock.getStockId(),
                    season.getCurrentTurn(), request.quantity(), price, reason.getTradeReasonId());
        } else {
            if (holding == null || !holding.hasEnoughQuantity(request.quantity())) {
                throw new BusinessException(TradingErrorCode.NOT_ENOUGH_QUANTITY);
            }
            holding.subtractSell(request.quantity());
            participant.receiveCash(amount);
            order = TradeOrder.createSell(participant.getSeasonParticipantId(), stock.getStockId(),
                    season.getCurrentTurn(), request.quantity(), price, reason.getTradeReasonId());
        }
        TradeOrder saved = orderRepository.save(order);
        return new OrderResultDTO(saved.getTradeOrderId(), price, amount, participant.getCash(),
                new OrderResultDTO.Holding(stock.getStockId(), holding.getQuantity(), holding.getAvgPrice()));
    }

    /** API 61 내 거래 내역. seasonId 가 없으면 지금 보이는 시즌. 참가하지 않았으면 빈 목록 */
    public MyOrderListDTO findMyOrders(Long memberId, Long seasonId, int page, int size) {
        if (memberId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        TradingSeason season = seasonId == null ? getVisibleSeason()
                : seasonRepository.findById(seasonId)
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        SeasonParticipant participant = participantRepository
                .findByTradingSeasonIdAndMemberId(season.getTradingSeasonId(), memberId).orElse(null);
        if (participant == null) {
            return new MyOrderListDTO(0, List.of());
        }
        Page<TradeOrder> orders = orderRepository.findBySeasonParticipantId(participant.getSeasonParticipantId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "tradeOrderId")));

        Map<Long, String> stockNames = stockRepository.findAllById(orders.getContent().stream()
                .map(TradeOrder::getStockId).distinct().toList()).stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName));
        Map<Long, String> reasons = reasonRepository.findAllById(orders.getContent().stream()
                .map(TradeOrder::getTradeReasonId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(TradeReason::getTradeReasonId, TradeReason::getLabel));
        return new MyOrderListDTO(orders.getTotalElements(), orders.getContent().stream()
                .map(order -> new MyOrderListDTO.Item(order.getTradeOrderId(), order.getTurnNo(),
                        stockNames.get(order.getStockId()), order.getOrderType(), order.getQuantity(),
                        order.getPrice(), reasons.get(order.getTradeReasonId())))
                .toList());
    }

    /** 매매 이유는 반드시 골라야 하고, 사용 중인 선택지여야 한다 (SP-57) */
    private TradeReason findReason(Long reasonId) {
        if (reasonId == null) {
            throw new BusinessException(TradingErrorCode.REASON_REQUIRED);
        }
        return reasonRepository.findById(reasonId)
                .filter(TradeReason::isActive)
                .orElseThrow(() -> new BusinessException(TradingErrorCode.REASON_REQUIRED));
    }

    /**
     * 지금 주문할 수 있는 시즌 : 진행 중이고, 화면이 본 턴이 현재 턴이어야 한다.
     * 시즌 번호만 먼저 읽고 시즌 행을 공유 잠금으로 읽은 뒤 상태·현재 턴을 확인한다. 턴 진행(TurnAdminService.advanceTurn)은
     * 같은 행을 배타 잠금으로 잡으므로, 주문이 끝나기 전에 턴이 넘어가 이전 턴 가격으로 체결되지 않는다.
     * 시즌 객체를 먼저 올리면 잠금을 기다린 뒤에도 오래된 상태를 보므로 번호만 읽는다
     */
    private TradingSeason lockOrderableSeason(int requestedTurn) {
        Long seasonId = seasonRepository.findSeasonIdsByStatusIn(TradingService.VISIBLE_STATUSES, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(TradingErrorCode.NO_RUNNING_SEASON));
        TradingSeason season = seasonRepository.findByIdForShare(seasonId)
                .orElseThrow(() -> new BusinessException(TradingErrorCode.NO_RUNNING_SEASON));
        if (season.isSettlingOrEnded()) {
            throw new BusinessException(TradingErrorCode.SEASON_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 상태를 휴장으로 바꾸지 않았어도 일요일은 휴장이다 (턴 점검과 같은 기준, SP-58)
        if (season.isHoliday() || TradingSeason.isClosedDay(now.toLocalDate())) {
            throw new BusinessException(TradingErrorCode.MARKET_HOLIDAY);
        }
        if (season.getCurrentTurn() < 1) {
            // 시즌은 열렸지만 아직 첫 턴이 시작되지 않았다 (관리자가 첫 턴을 넘기기 전)
            throw new BusinessException(TradingErrorCode.NO_RUNNING_SEASON);
        }
        // 정규장 08:00~20:00 밖에는 주문을 받지 않는다 (#208, SP-58)
        if (!MarketHours.isOpen(now)) {
            throw new BusinessException(TradingErrorCode.MARKET_CLOSED);
        }
        if (requestedTurn != season.getCurrentTurn()) {
            throw new BusinessException(TradingErrorCode.STALE_TURN_PRICE);
        }
        return season;
    }

    /** 사용 중 종목 (사용 안 함 분야의 종목은 거래할 수 없다). 없으면 NOT_FOUND */
    private Stock getTradableStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .filter(Stock::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        sectorRepository.findById(stock.getSectorId())
                .filter(Sector::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return stock;
    }

    private TradingSeason getVisibleSeason() {
        return seasonRepository.findFirstByStatusInOrderByStartDateDesc(TradingService.VISIBLE_STATUSES)
                .orElseThrow(() -> new BusinessException(TradingErrorCode.NO_RUNNING_SEASON));
    }
}
