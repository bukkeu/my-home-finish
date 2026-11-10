package com.wanted.moneybeetle.domain.trading.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeReasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.RankingAward;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (모의투자)
*   #45 관리자 참가자 관리·랭킹 미리보기 (API 135~137, ADM-28, ADM-31)
*   - 참가자 목록 : 현재 순자산·수익률은 저장된 값이 아니라 지금 보유 종목과 현재 턴 가격으로 계산한다 (PortfolioValuer).
*     순자산·수익률로 정렬하려면 전원을 계산해야 해서 전원을 계산한 뒤 쪽을 자른다. 레벨은 그 쪽의 회원만 조회한다
*   - 참가자 상세 : 보유 종목, 거래 내역과 매매 이유
*   - 랭킹 미리보기 : 시즌 중에도 관리자만(/api/v1/admin/** 는 ADMIN 만). 응답은 시즌 랭킹(API 62)과 같고, 수상·내 순위는 없다
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminParticipantService {

    public static final String SORT_NET_ASSET = "netAsset";
    public static final String SORT_RETURN_RATE = "returnRate";
    public static final String SORT_INVEST_AMOUNT = "investAmount";
    public static final String SORT_JOINED_AT = "joinedAt";
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private final TradingSeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final HoldingRepository holdingRepository;
    private final TradeOrderRepository orderRepository;
    private final TradeReasonRepository reasonRepository;
    private final StockRepository stockRepository;
    private final PortfolioValuer valuer;
    private final MemberService memberService;
    private final GrowthService growthService;

    /** API 135 참가자 목록. sort : netAsset(기본)·returnRate·investAmount 는 큰 순, joinedAt 은 먼저 참가한 순 */
    public AdminParticipantListDTO findParticipants(Long seasonId, String sort, int page, int size) {
        String sortKey = sort == null || sort.isBlank() ? SORT_NET_ASSET : sort;
        checkPaging(page, size);
        TradingSeason season = getSeason(seasonId);
        List<SeasonParticipant> participants =
                participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(seasonId);
        Map<Long, PortfolioValuer.Valuation> valuations = valuer.value(season, participants);

        List<SeasonParticipant> sorted = participants.stream()
                .sorted(participantOrder(sortKey, valuations))
                .toList();
        List<SeasonParticipant> pageRows = sorted.stream().skip((long) page * size).limit(size).toList();

        Map<Long, String> nicknames = memberService.findNicknames(
                pageRows.stream().map(SeasonParticipant::getMemberId).toList());
        List<AdminParticipantListDTO.Item> items = pageRows.stream()
                .map(row -> {
                    PortfolioValuer.Valuation valuation = valuations.get(row.getSeasonParticipantId());
                    return new AdminParticipantListDTO.Item(row.getSeasonParticipantId(),
                            nicknames.get(row.getMemberId()), growthService.getLevel(row.getMemberId()),
                            row.getInvestAmount(), valuation.netAsset(), valuation.returnRate());
                })
                .toList();
        return new AdminParticipantListDTO(participants.size(), items);
    }

    /** API 136 참가자 상세 */
    public AdminParticipantDetailDTO getParticipant(Long participantId) {
        SeasonParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        TradingSeason season = getSeason(participant.getTradingSeasonId());
        PortfolioValuer.Valuation valuation = valuer.value(season, List.of(participant))
                .get(participant.getSeasonParticipantId());

        List<Holding> holdings = holdingRepository.findBySeasonParticipantIdOrderByHoldingIdAsc(participantId)
                .stream().filter(holding -> !holding.isEmpty()).toList();
        List<TradeOrder> orders = orderRepository.findBySeasonParticipantIdOrderByTradeOrderIdDesc(participantId);

        Map<Long, String> stockNames = stockRepository.findAllById(java.util.stream.Stream.concat(
                        holdings.stream().map(Holding::getStockId), orders.stream().map(TradeOrder::getStockId))
                .distinct().toList()).stream().collect(Collectors.toMap(Stock::getStockId, Stock::getName));
        Map<Long, String> reasons = reasonRepository.findAllById(orders.stream().map(TradeOrder::getTradeReasonId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(TradeReason::getTradeReasonId, TradeReason::getLabel));
        Map<Long, Integer> prices = valuer.pricesAtValuationTurn(season);

        List<AdminParticipantDetailDTO.HoldingItem> holdingItems = holdings.stream()
                .map(holding -> {
                    int currentPrice = PortfolioValuer.priceOf(holding, prices);
                    return new AdminParticipantDetailDTO.HoldingItem(holding.getStockId(),
                            stockNames.get(holding.getStockId()), holding.getQuantity(), holding.getAvgPrice(),
                            currentPrice, (long) holding.getQuantity() * currentPrice);
                })
                .toList();
        List<AdminParticipantDetailDTO.OrderItem> orderItems = orders.stream()
                .map(order -> new AdminParticipantDetailDTO.OrderItem(order.getTradeOrderId(), order.getTurnNo(),
                        stockNames.get(order.getStockId()), order.getOrderType(), order.getQuantity(),
                        order.getPrice(), order.getAmount(), reasons.get(order.getTradeReasonId()),
                        order.getCreatedAt()))
                .toList();

        String nickname = memberService.findNicknames(List.of(participant.getMemberId()))
                .get(participant.getMemberId());
        return new AdminParticipantDetailDTO(participantId, season.getTradingSeasonId(), nickname,
                growthService.getLevel(participant.getMemberId()), participant.getInvestAmount(),
                participant.getCash(), valuation.netAsset(), valuation.returnRate(), holdingItems, orderItems);
    }

    /** API 137 랭킹 미리보기 (type : ASSET 기본 / RETURN). 시즌 중 현재 가격 기준의 임시 순위라서 공개 여부와 상관없이 본다 */
    public RankingDTO previewRankings(Long seasonId, String type, int page, int size) {
        String rankingType = type == null || type.isBlank() ? RankingAward.TYPE_ASSET : type;
        if (!RankingAward.TYPE_ASSET.equals(rankingType) && !RankingAward.TYPE_RETURN.equals(rankingType)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (size > RankingService.MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        checkPaging(page, size);
        TradingSeason season = getSeason(seasonId);
        List<SeasonParticipant> participants =
                participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(seasonId);
        Map<Long, PortfolioValuer.Valuation> valuations = valuer.value(season, participants);

        boolean asset = RankingAward.TYPE_ASSET.equals(rankingType);
        List<PortfolioValuer.Ranked<SeasonParticipant>> ranked = PortfolioValuer.rank(participants,
                participant -> asset ? (Comparable<?>) valuations.get(participant.getSeasonParticipantId()).netAsset()
                        : valuations.get(participant.getSeasonParticipantId()).returnRate());
        List<PortfolioValuer.Ranked<SeasonParticipant>> pageRows =
                ranked.stream().skip((long) page * size).limit(size).toList();

        Map<Long, String> nicknames = memberService.findNicknames(
                pageRows.stream().map(row -> row.item().getMemberId()).toList());
        List<RankingDTO.Item> items = pageRows.stream()
                .map(row -> {
                    PortfolioValuer.Valuation valuation = valuations.get(row.item().getSeasonParticipantId());
                    return new RankingDTO.Item(row.rank(), nicknames.get(row.item().getMemberId()), null,
                            valuation.netAsset(), valuation.returnRate(), false);
                })
                .toList();
        return new RankingDTO(season.getName(), rankingType, participants.size(), items, null);
    }

    private Comparator<SeasonParticipant> participantOrder(String sortKey,
            Map<Long, PortfolioValuer.Valuation> valuations) {
        Comparator<SeasonParticipant> byId = Comparator.comparing(SeasonParticipant::getSeasonParticipantId);
        return switch (sortKey) {
            case SORT_NET_ASSET -> Comparator.<SeasonParticipant, Long>comparing(
                    p -> valuations.get(p.getSeasonParticipantId()).netAsset()).reversed().thenComparing(byId);
            case SORT_RETURN_RATE -> Comparator.<SeasonParticipant, java.math.BigDecimal>comparing(
                    p -> valuations.get(p.getSeasonParticipantId()).returnRate()).reversed().thenComparing(byId);
            case SORT_INVEST_AMOUNT -> Comparator.comparing(SeasonParticipant::getInvestAmount).reversed()
                    .thenComparing(byId);
            case SORT_JOINED_AT -> byId;
            default -> throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        };
    }

    private static void checkPaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private TradingSeason getSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
