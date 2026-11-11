package com.wanted.moneybeetle.domain.trading.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.RankingAwardRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeReasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingPublishedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingRewardResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatisticsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SettleResultDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.RankingAward;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (모의투자)
*   #46 관리자 정산·랭킹 공개·보상·통계 (API 138~141, ADM-32~35, INV-04, SP-61~64)
*   - 정산 : 정산 중(SETTLING)인 시즌을 한 번만 실행한다. 최종 순자산·수익률·순위·이월금을 한 트랜잭션으로 확정한다.
*     이월금 = 최종 순자산 x 이월 비율, 상한이 있으면 상한까지, 0 미만이면 0 (원 단위 내림, SP-63). 다음 시즌 참가가 이 값을 읽는다
*   - 랭킹 공개 : 정산을 마친 뒤에만 한다. 공개하면 시즌이 종료(ENDED)되고, 상위 3위 보상을 함께 지급한다 (SP-64 "랭킹 공개 때 지급")
*   - 보상 지급 : 순자산·수익률 랭킹 각각 상위 3위에게 뱃지·칭호(ranking_award)와 캐시 50,000 / 30,000 / 10,000.
*     이미 받은 수상은 건너뛰어 여러 번 눌러도 한 번만 지급된다. 두 랭킹에 모두 들면 둘 다 받는다
*   - 통계 : 평균 수익률, 많이 산 종목(매수 건수 상위 3), 매매 이유 비율, 급락 턴(변동률 -10% 이하)에 판 사람 비율
*  */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    static final String ACTION_SETTLE = "SETTLE";
    static final String ACTION_RANKING_PUBLISH = "RANKING_PUBLISH";
    static final String ACTION_RANKING_REWARD = "RANKING_REWARD";
    static final String TARGET_TYPE_SEASON = "SEASON";

    /** 순위별 보상 캐시 (SP-64, #76 확정). 1위 50,000 / 2위 30,000 / 3위 10,000 */
    static final long[] REWARD_CASH = {50_000L, 30_000L, 10_000L};

    /** 급락 턴 : 그 종목의 지난 턴 대비 변동률이 이 값 이하인 턴 (%) */
    static final BigDecimal PANIC_DROP_RATE = new BigDecimal("-10");

    static final int TOP_BOUGHT_COUNT = 3;

    private final TradingSeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final RankingAwardRepository awardRepository;
    private final TradeOrderRepository orderRepository;
    private final TradeReasonRepository reasonRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final PortfolioValuer valuer;
    private final RewardService rewardService;
    private final MemberService memberService;
    private final AdminLogService adminLogService;
    private final Clock clock;

    /** API 138 정산 실행. 정산은 한 번만 : 정산 중이 아니거나 이미 정산했으면 INVALID_STATUS_TRANSITION */
    @Transactional
    public SettleResultDTO settle(Long seasonId, Long adminId) {
        TradingAdminCheck.requireAdmin(adminId);
        TradingSeason season = lockSeason(seasonId);
        // 정산 중이 아니거나 이미 정산했으면 엔티티가 거절한다. 계산 전에 먼저 기록해 헛계산을 하지 않는다
        LocalDateTime now = LocalDateTime.now(clock);
        season.completeSettlement(now);

        List<SeasonParticipant> participants =
                participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(seasonId);
        Map<Long, PortfolioValuer.Valuation> valuations = valuer.value(season, participants);

        Map<Long, Integer> assetRanks = ranksOf(PortfolioValuer.rank(participants,
                p -> valuations.get(p.getSeasonParticipantId()).netAsset()));
        Map<Long, Integer> returnRanks = ranksOf(PortfolioValuer.rank(participants,
                p -> valuations.get(p.getSeasonParticipantId()).returnRate()));
        for (SeasonParticipant participant : participants) {
            PortfolioValuer.Valuation valuation = valuations.get(participant.getSeasonParticipantId());
            participant.settle(valuation.netAsset(), valuation.returnRate(),
                    carryoverOf(valuation.netAsset(), season));
            participant.changeRanks(assetRanks.get(participant.getSeasonParticipantId()),
                    returnRanks.get(participant.getSeasonParticipantId()));
        }

        adminLogService.record(adminId, ACTION_SETTLE, TARGET_TYPE_SEASON, seasonId,
                "{\"participants\":" + participants.size() + "}");
        return new SettleResultDTO(participants.size(), now);
    }

    /** API 139 랭킹 공개 : 정산 뒤에만. 공개하면 시즌이 끝나고 상위 3위 보상을 함께 지급한다 */
    @Transactional
    public RankingPublishedDTO publishRanking(Long seasonId, Long adminId) {
        TradingAdminCheck.requireAdmin(adminId);
        TradingSeason season = lockSeason(seasonId);
        // 정산 중이 아니면 INVALID_STATUS_TRANSITION, 정산 전이면 NOT_SETTLED (엔티티가 거절한다)
        LocalDateTime now = LocalDateTime.now(clock);
        season.publishRanking(now);

        int awarded = grantRewards(season, adminId);
        adminLogService.record(adminId, ACTION_RANKING_PUBLISH, TARGET_TYPE_SEASON, seasonId,
                "{\"awarded\":" + awarded + "}");
        return new RankingPublishedDTO(now);
    }

    /** API 140 랭킹 보상 지급 : 공개된 시즌만. 이미 지급한 수상은 건너뛴다 (다시 눌러도 안전) */
    @Transactional
    public RankingRewardResultDTO grantRankingRewards(Long seasonId, Long adminId) {
        TradingAdminCheck.requireAdmin(adminId);
        TradingSeason season = lockSeason(seasonId);
        if (!season.isRankingPublished()) {
            throw new BusinessException(TradingErrorCode.RANKING_NOT_PUBLISHED);
        }
        int awarded = grantRewards(season, adminId);
        adminLogService.record(adminId, ACTION_RANKING_REWARD, TARGET_TYPE_SEASON, seasonId,
                "{\"awarded\":" + awarded + "}");
        return new RankingRewardResultDTO(awarded);
    }

    /** API 141 시즌 통계 */
    public SeasonStatisticsDTO findStatistics(Long seasonId) {
        TradingSeason season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<SeasonParticipant> participants =
                participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(seasonId);
        return new SeasonStatisticsDTO(averageReturnRate(season, participants), topBoughtStocks(seasonId),
                reasonRatio(seasonId), panicSellRate(seasonId));
    }

    // ===== 정산 =====

    /** 다음 시즌 이월금 = 최종 순자산 x 이월 비율(%) , 상한이 있으면 상한까지, 0 미만이면 0. 원 단위 내림 (SP-63) */
    static long carryoverOf(long finalNetAsset, TradingSeason season) {
        if (finalNetAsset <= 0) {
            return 0L;
        }
        long carryover = BigDecimal.valueOf(finalNetAsset).multiply(season.getCarryoverRate())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValue();
        if (season.getCarryoverCap() != null) {
            carryover = Math.min(carryover, season.getCarryoverCap());
        }
        return Math.max(carryover, 0L);
    }

    private static Map<Long, Integer> ranksOf(List<PortfolioValuer.Ranked<SeasonParticipant>> ranked) {
        return ranked.stream().collect(Collectors.toMap(row -> row.item().getSeasonParticipantId(),
                PortfolioValuer.Ranked::rank));
    }

    // ===== 보상 =====

    /** 순자산·수익률 랭킹 각각 상위 3위에게 수상 기록과 캐시를 준다. 이번에 새로 지급한 수상 수를 돌려준다 */
    private int grantRewards(TradingSeason season, Long adminId) {
        List<SeasonParticipant> participants = participantRepository
                .findByTradingSeasonIdOrderBySeasonParticipantIdAsc(season.getTradingSeasonId());
        int awarded = 0;
        for (SeasonParticipant participant : participants) {
            awarded += awardIfTop3(season, participant, RankingAward.TYPE_ASSET, participant.getAssetRank(), adminId);
            awarded += awardIfTop3(season, participant, RankingAward.TYPE_RETURN, participant.getReturnRank(), adminId);
        }
        return awarded;
    }

    private int awardIfTop3(TradingSeason season, SeasonParticipant participant, String type, Integer rank,
            Long adminId) {
        if (rank == null || rank < 1 || rank > REWARD_CASH.length) {
            return 0;
        }
        Long seasonId = season.getTradingSeasonId();
        Long memberId = participant.getMemberId();
        if (awardRepository.existsByTradingSeasonIdAndMemberIdAndRankingType(seasonId, memberId, type)) {
            return 0;
        }
        // 탈퇴·정지 등으로 캐시를 줄 수 없는 회원은 건너뛴다 (지급 실패로 공개 전체가 되돌려지지 않게)
        if (!memberService.isActiveMember(memberId)) {
            log.warn("랭킹 보상을 건너뜁니다 (활동 중인 회원이 아님): seasonId={}, memberId={}, type={}", seasonId, memberId, type);
            return 0;
        }
        String label = RankingAward.TYPE_ASSET.equals(type) ? "순자산" : "수익률";
        String badge = badgeName(season.getName(), label, rank);
        RankingAward award = RankingAward.TYPE_ASSET.equals(type)
                ? RankingAward.createAssetAward(seasonId, memberId, rank, badge)
                : RankingAward.createReturnAward(seasonId, memberId, rank, badge);
        awardRepository.save(award);
        rewardService.adjustByAdmin(adminId, memberId, true, REWARD_CASH[rank - 1], badge + " 보상");
        return 1;
    }

    /** 뱃지·칭호 이름 (badge_name 은 50자까지). 시즌 이름이 길면 줄인다 */
    static String badgeName(String seasonName, String rankingLabel, int rank) {
        String suffix = " " + rankingLabel + " " + rank + "위";
        int maxSeasonLength = 50 - suffix.length();
        String name = seasonName.length() > maxSeasonLength ? seasonName.substring(0, maxSeasonLength) : seasonName;
        return name + suffix;
    }

    // ===== 통계 =====

    private BigDecimal averageReturnRate(TradingSeason season, List<SeasonParticipant> participants) {
        if (participants.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        Map<Long, PortfolioValuer.Valuation> valuations = valuer.value(season, participants);
        BigDecimal sum = valuations.values().stream().map(PortfolioValuer.Valuation::returnRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
    }

    /** 매수 건수가 많은 종목 이름 (같으면 종목 번호 순) */
    private List<String> topBoughtStocks(Long seasonId) {
        List<TradeOrderRepository.StockOrderCount> counts = orderRepository.countBuysByStock(seasonId).stream()
                .sorted(Comparator.comparingLong(TradeOrderRepository.StockOrderCount::getOrderCount).reversed()
                        .thenComparing(TradeOrderRepository.StockOrderCount::getStockId))
                .limit(TOP_BOUGHT_COUNT)
                .toList();
        Map<Long, String> names = stockRepository.findAllById(counts.stream()
                        .map(TradeOrderRepository.StockOrderCount::getStockId).toList()).stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName));
        return counts.stream().map(count -> names.get(count.getStockId())).toList();
    }

    /** 매매 이유별 주문 비율(%, 소수 첫째 자리). 주문이 없으면 빈 맵 */
    private Map<String, BigDecimal> reasonRatio(Long seasonId) {
        List<TradeOrderRepository.ReasonOrderCount> counts = orderRepository.countOrdersByReason(seasonId);
        long total = counts.stream().mapToLong(TradeOrderRepository.ReasonOrderCount::getOrderCount).sum();
        if (total == 0) {
            return Map.of();
        }
        Map<Long, String> labels = reasonRepository.findAllById(counts.stream()
                        .map(TradeOrderRepository.ReasonOrderCount::getReasonId).filter(id -> id != null).toList())
                .stream().collect(Collectors.toMap(TradeReason::getTradeReasonId, TradeReason::getLabel));
        Map<String, BigDecimal> ratio = new LinkedHashMap<>();
        counts.stream()
                .filter(count -> count.getReasonId() != null && labels.containsKey(count.getReasonId()))
                .sorted(Comparator.comparingLong(TradeOrderRepository.ReasonOrderCount::getOrderCount).reversed()
                        .thenComparing(TradeOrderRepository.ReasonOrderCount::getReasonId))
                .forEach(count -> ratio.put(labels.get(count.getReasonId()), BigDecimal.valueOf(count.getOrderCount())
                        .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)));
        return ratio;
    }

    /**
     * 급락 턴에 판 사람 비율(%) = 급락 턴(그 종목 변동률 -10% 이하)에 그 종목을 판 적이 있는 참가자 수
     * ÷ 한 번이라도 판 참가자 수 x 100. 파는 사람이 없으면 0
     */
    private BigDecimal panicSellRate(Long seasonId) {
        List<TradeOrder> sells = orderRepository.findSellOrdersBySeason(seasonId);
        if (sells.isEmpty()) {
            return BigDecimal.ZERO.setScale(1);
        }
        Map<String, BigDecimal> changeRates = new java.util.HashMap<>();
        for (StockPrice price : priceRepository.findByTradingSeasonId(seasonId)) {
            if (price.getChangeRate() != null) {
                changeRates.put(price.getStockId() + ":" + price.getTurnNo(), price.getChangeRate());
            }
        }
        Set<Long> sellers = new HashSet<>();
        Set<Long> panicSellers = new HashSet<>();
        for (TradeOrder sell : sells) {
            sellers.add(sell.getSeasonParticipantId());
            BigDecimal rate = changeRates.get(sell.getStockId() + ":" + sell.getTurnNo());
            if (rate != null && rate.compareTo(PANIC_DROP_RATE) <= 0) {
                panicSellers.add(sell.getSeasonParticipantId());
            }
        }
        return BigDecimal.valueOf(panicSellers.size()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(sellers.size()), 1, RoundingMode.HALF_UP);
    }

    private TradingSeason lockSeason(Long seasonId) {
        return seasonRepository.findByIdForUpdate(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
