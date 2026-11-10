package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
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
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.RankingAward;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#46)
*   SettlementService 확인 (DB 없이 Repository·다른 Service 는 가짜(mock))
*   - 정산 : 한 번만, 최종 순자산·수익률·순위(동점은 같은 순위)·이월금(비율·상한) 확정, 관리자 로그
*   - 랭킹 공개 : 정산 뒤에만, 시즌 종료, 상위 3위 보상, 지급은 한 번만
*   - 통계 : 평균 수익률, 많이 산 종목, 매매 이유 비율, 급락 턴 매도 비율
*  */
class SettlementServiceTest {

    private static final long SEASON_ID = 7L;
    private static final long ADMIN_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T00:10:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 0, 10);

    private TradingSeasonRepository seasonRepository;
    private SeasonParticipantRepository participantRepository;
    private RankingAwardRepository awardRepository;
    private TradeOrderRepository orderRepository;
    private TradeReasonRepository reasonRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private HoldingRepository holdingRepository;
    private RewardService rewardService;
    private MemberService memberService;
    private AdminLogService adminLogService;
    private SettlementService service;
    private TradingSeason season;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        awardRepository = mock(RankingAwardRepository.class);
        orderRepository = mock(TradeOrderRepository.class);
        reasonRepository = mock(TradeReasonRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        holdingRepository = mock(HoldingRepository.class);
        rewardService = mock(RewardService.class);
        memberService = mock(MemberService.class);
        adminLogService = mock(AdminLogService.class);
        service = new SettlementService(seasonRepository, participantRepository, awardRepository, orderRepository,
                reasonRepository, stockRepository, priceRepository, new PortfolioValuer(holdingRepository,
                priceRepository), rewardService, memberService, adminLogService, CLOCK);

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        for (int i = 0; i < 12; i++) {
            season.advanceTurn();
        }
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        when(seasonRepository.findByIdForUpdate(SEASON_ID)).thenReturn(Optional.of(season));
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 12)).thenReturn(List.of(
                StockPrice.create(SEASON_ID, 1L, 12, 100_000, new BigDecimal("1.00"))));
    }

    /** 참가자 번호 = 회원 번호 / 10. 투자 1,000,000 에 현금만 정한다 */
    private SeasonParticipant participant(Long id, long cash) {
        SeasonParticipant participant = SeasonParticipant.create(SEASON_ID, id * 10, 1_000_000, 0);
        ReflectionTestUtils.setField(participant, "seasonParticipantId", id);
        ReflectionTestUtils.setField(participant, "cash", cash);
        return participant;
    }

    private List<SeasonParticipant> givenParticipants(SeasonParticipant... participants) {
        List<SeasonParticipant> list = List.of(participants);
        when(participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(SEASON_ID)).thenReturn(list);
        when(holdingRepository.findBySeasonParticipantIdIn(any())).thenReturn(List.of());
        return list;
    }

    private void assertRejected(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ===== 정산 =====

    @Test
    void 정산은_최종_순자산_수익률_순위_이월금을_확정하고_기록을_남긴다() {
        Holding holding = Holding.create(1L, 1L);
        holding.addBuy(5, 90_000);
        SeasonParticipant a = participant(1L, 500_000);           // 500,000 + 5 x 100,000 = 1,000,000
        SeasonParticipant b = participant(2L, 2_000_000);
        SeasonParticipant c = participant(3L, 800_000);
        givenParticipants(a, b, c);
        when(holdingRepository.findBySeasonParticipantIdIn(any())).thenReturn(List.of(holding));

        SettleResultDTO result = service.settle(SEASON_ID, ADMIN_ID);

        assertThat(result.participants()).isEqualTo(3);
        assertThat(result.settledAt()).isEqualTo(NOW);
        assertThat(season.isSettled()).isTrue();
        assertThat(b.getFinalNetAsset()).isEqualTo(2_000_000);
        assertThat(b.getReturnRate()).isEqualByComparingTo("100.00");
        assertThat(b.getAssetRank()).isEqualTo(1);
        assertThat(a.getAssetRank()).isEqualTo(2);
        assertThat(c.getAssetRank()).isEqualTo(3);
        assertThat(a.getReturnRank()).isEqualTo(2);
        // 이월금 = 최종 순자산 전액 (이월 100%, 상한 없음, 손실이어도 그대로)
        assertThat(c.getCarryoverOut()).isEqualTo(800_000);
        assertThat(a.getCarryoverOut()).isEqualTo(1_000_000);
        assertThat(a.isSettled()).isTrue();
        verify(adminLogService).record(ADMIN_ID, "SETTLE", "SEASON", SEASON_ID, "{\"participants\":3}");
    }

    @Test
    void 동점은_같은_순위로_확정한다() {
        SeasonParticipant a = participant(1L, 2_000_000);
        SeasonParticipant b = participant(2L, 2_000_000);
        SeasonParticipant c = participant(3L, 500_000);
        givenParticipants(a, b, c);

        service.settle(SEASON_ID, ADMIN_ID);

        assertThat(a.getAssetRank()).isEqualTo(1);
        assertThat(b.getAssetRank()).isEqualTo(1);
        assertThat(c.getAssetRank()).isEqualTo(3);
        assertThat(c.getReturnRank()).isEqualTo(3);
    }

    @Test
    void 이월금은_이월_비율과_상한을_적용하고_원_단위_내림이다() {
        TradingSeason half = TradingSeason.create("시즌 8", LocalDate.of(2026, 11, 16), new BigDecimal("50.00"),
                700_000L, 1L);

        assertThat(SettlementService.carryoverOf(1_000_001, half)).isEqualTo(500_000);   // 500,000.5 → 내림
        assertThat(SettlementService.carryoverOf(2_000_000, half)).isEqualTo(700_000);   // 상한
        assertThat(SettlementService.carryoverOf(0, half)).isZero();
        assertThat(SettlementService.carryoverOf(-5, half)).isZero();
        assertThat(SettlementService.carryoverOf(3_333_333, season)).isEqualTo(3_333_333);   // 기본 100%·상한 없음
    }

    @Test
    void 정산은_한_번만_하고_정산_중이_아니면_INVALID_STATUS_TRANSITION() {
        givenParticipants(participant(1L, 1_000_000));
        service.settle(SEASON_ID, ADMIN_ID);

        assertRejected(() -> service.settle(SEASON_ID, ADMIN_ID), TradingErrorCode.INVALID_STATUS_TRANSITION);

        TradingSeason running = TradingSeason.create("시즌 9", LocalDate.of(2026, 11, 30), 1L);
        running.changeStatus(TradingSeason.STATUS_RUNNING);
        ReflectionTestUtils.setField(running, "tradingSeasonId", 9L);
        when(seasonRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(running));
        assertRejected(() -> service.settle(9L, ADMIN_ID), TradingErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 로그인한_관리자가_없거나_없는_시즌이면_거절한다() {
        assertRejected(() -> service.settle(SEASON_ID, null), CommonErrorCode.UNAUTHORIZED);
        assertRejected(() -> service.settle(99L, ADMIN_ID), CommonErrorCode.NOT_FOUND);
        assertRejected(() -> service.publishRanking(SEASON_ID, null), CommonErrorCode.UNAUTHORIZED);
        assertRejected(() -> service.grantRankingRewards(SEASON_ID, null), CommonErrorCode.UNAUTHORIZED);
        verify(adminLogService, never()).record(anyLong(), anyString(), anyString(), anyLong(), any());
    }

    // ===== 랭킹 공개·보상 =====

    private void givenSettledTop(SeasonParticipant... participants) {
        givenParticipants(participants);
        service.settle(SEASON_ID, ADMIN_ID);
    }

    @Test
    void 정산_전에는_랭킹을_공개할_수_없다() {
        assertRejected(() -> service.publishRanking(SEASON_ID, ADMIN_ID), TradingErrorCode.NOT_SETTLED);
        assertThat(season.isRankingPublished()).isFalse();
    }

    @Test
    void 랭킹을_공개하면_시즌이_끝나고_상위_3위에게_수상과_캐시를_준다() {
        SeasonParticipant first = participant(1L, 3_000_000);
        SeasonParticipant second = participant(2L, 2_000_000);
        SeasonParticipant third = participant(3L, 1_500_000);
        SeasonParticipant fourth = participant(4L, 500_000);
        givenSettledTop(first, second, third, fourth);

        RankingPublishedDTO published = service.publishRanking(SEASON_ID, ADMIN_ID);

        assertThat(published.publishedAt()).isEqualTo(NOW);
        assertThat(season.isEnded()).isTrue();
        assertThat(season.isRankingPublished()).isTrue();
        // 순자산·수익률 랭킹 각각 3명 = 6건. 두 랭킹에 모두 든 회원은 둘 다 받는다
        verify(awardRepository, times(6)).save(any(RankingAward.class));
        verify(rewardService).adjustByAdmin(eq(ADMIN_ID), eq(10L), eq(true), eq(50_000L), eq("시즌 7 순자산 1위 보상"));
        verify(rewardService).adjustByAdmin(eq(ADMIN_ID), eq(10L), eq(true), eq(50_000L), eq("시즌 7 수익률 1위 보상"));
        verify(rewardService).adjustByAdmin(eq(ADMIN_ID), eq(20L), eq(true), eq(30_000L), eq("시즌 7 순자산 2위 보상"));
        verify(rewardService).adjustByAdmin(eq(ADMIN_ID), eq(30L), eq(true), eq(10_000L), eq("시즌 7 순자산 3위 보상"));
        verify(rewardService, never()).adjustByAdmin(anyLong(), eq(40L), anyBoolean(), anyLong(), anyString());
        verify(adminLogService).record(ADMIN_ID, "RANKING_PUBLISH", "SEASON", SEASON_ID, "{\"awarded\":6}");

        ArgumentCaptor<RankingAward> awards = ArgumentCaptor.forClass(RankingAward.class);
        verify(awardRepository, times(6)).save(awards.capture());
        assertThat(awards.getAllValues()).extracting(RankingAward::getBadgeName)
                .contains("시즌 7 순자산 1위", "시즌 7 수익률 3위");
    }

    @Test
    void 공개된_시즌만_보상을_지급하고_이미_받은_수상은_건너뛴다() {
        SeasonParticipant first = participant(1L, 3_000_000);
        givenSettledTop(first);
        assertRejected(() -> service.grantRankingRewards(SEASON_ID, ADMIN_ID), TradingErrorCode.RANKING_NOT_PUBLISHED);

        service.publishRanking(SEASON_ID, ADMIN_ID);
        when(awardRepository.existsByTradingSeasonIdAndMemberIdAndRankingType(SEASON_ID, 10L, "ASSET")).thenReturn(true);
        when(awardRepository.existsByTradingSeasonIdAndMemberIdAndRankingType(SEASON_ID, 10L, "RETURN")).thenReturn(true);

        RankingRewardResultDTO again = service.grantRankingRewards(SEASON_ID, ADMIN_ID);

        assertThat(again.awarded()).isZero();
        verify(rewardService, times(2)).adjustByAdmin(anyLong(), anyLong(), anyBoolean(), anyLong(), anyString());   // 공개 때 2건뿐
    }

    @Test
    void 활동_중이_아닌_회원은_보상을_건너뛰고_공개는_그대로_된다() {
        SeasonParticipant first = participant(1L, 3_000_000);
        SeasonParticipant second = participant(2L, 2_000_000);
        givenSettledTop(first, second);
        when(memberService.isActiveMember(10L)).thenReturn(false);

        service.publishRanking(SEASON_ID, ADMIN_ID);

        assertThat(season.isEnded()).isTrue();
        verify(rewardService, never()).adjustByAdmin(anyLong(), eq(10L), anyBoolean(), anyLong(), anyString());
        // 2번 회원은 순자산 2위·수익률 2위 둘 다 받는다
        verify(rewardService, times(2)).adjustByAdmin(eq(ADMIN_ID), eq(20L), eq(true), eq(30_000L), anyString());
    }

    @Test
    void 이미_공개했거나_종료된_시즌은_다시_공개할_수_없다() {
        givenSettledTop(participant(1L, 3_000_000));
        service.publishRanking(SEASON_ID, ADMIN_ID);

        assertRejected(() -> service.publishRanking(SEASON_ID, ADMIN_ID), TradingErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 뱃지_이름은_50자를_넘지_않는다() {
        String longName = "가".repeat(60);

        String badge = SettlementService.badgeName(longName, "순자산", 1);

        assertThat(badge).hasSize(50).endsWith(" 순자산 1위");
        assertThat(SettlementService.badgeName("시즌 7", "수익률", 3)).isEqualTo("시즌 7 수익률 3위");
    }

    // ===== 통계 =====

    @Test
    void 통계는_평균_수익률_많이_산_종목_매매_이유_비율_급락_턴_매도_비율을_준다() {
        givenParticipants(participant(1L, 1_200_000), participant(2L, 800_000));   // +20%, -20% → 평균 0
        TradeOrderRepository.StockOrderCount top = stockCount(1L, 9);
        TradeOrderRepository.StockOrderCount second = stockCount(2L, 4);
        when(orderRepository.countBuysByStock(SEASON_ID)).thenReturn(List.of(second, top));
        Stock s1 = Stock.create(1L, "한빛반도체", 80_000);
        ReflectionTestUtils.setField(s1, "stockId", 1L);
        Stock s2 = Stock.create(1L, "모아모빌리티", 50_000);
        ReflectionTestUtils.setField(s2, "stockId", 2L);
        when(stockRepository.findAllById(any())).thenReturn(List.of(s1, s2));
        TradeOrderRepository.ReasonOrderCount newsCount = reasonCount(1L, 3);
        TradeOrderRepository.ReasonOrderCount splitCount = reasonCount(2L, 1);
        when(orderRepository.countOrdersByReason(SEASON_ID)).thenReturn(List.of(newsCount, splitCount));
        TradeReason r1 = TradeReason.create("뉴스를 보고", 1);
        ReflectionTestUtils.setField(r1, "tradeReasonId", 1L);
        TradeReason r2 = TradeReason.create("나눠 담으려고", 2);
        ReflectionTestUtils.setField(r2, "tradeReasonId", 2L);
        when(reasonRepository.findAllById(any())).thenReturn(List.of(r1, r2));
        // 매도 3건: 1번 참가자가 급락 턴(-12%)에 판 것 1건, 2번 참가자는 평범한 턴에만 판다 → 2명 중 1명 = 50.0%
        TradeOrder panicSell = TradeOrder.createSell(1L, 1L, 4, 1, 90_000, 1L);
        TradeOrder normalSell = TradeOrder.createSell(1L, 1L, 5, 1, 95_000, 1L);
        TradeOrder otherSell = TradeOrder.createSell(2L, 1L, 5, 1, 95_000, 2L);
        when(orderRepository.findSellOrdersBySeason(SEASON_ID)).thenReturn(List.of(panicSell, normalSell, otherSell));
        when(priceRepository.findByTradingSeasonId(SEASON_ID)).thenReturn(List.of(
                StockPrice.create(SEASON_ID, 1L, 4, 90_000, new BigDecimal("-12.00")),
                StockPrice.create(SEASON_ID, 1L, 5, 95_000, new BigDecimal("5.56"))));

        SeasonStatisticsDTO stats = service.findStatistics(SEASON_ID);

        assertThat(stats.avgReturnRate()).isEqualByComparingTo("0.00");
        assertThat(stats.topBought()).containsExactly("한빛반도체", "모아모빌리티");
        assertThat(stats.reasonRatio()).containsOnlyKeys("뉴스를 보고", "나눠 담으려고");
        assertThat(stats.reasonRatio().get("뉴스를 보고")).isEqualByComparingTo("75.0");
        assertThat(stats.reasonRatio().get("나눠 담으려고")).isEqualByComparingTo("25.0");
        assertThat(stats.panicSellRate()).isEqualByComparingTo("50.0");
    }

    @Test
    void 참가자도_주문도_없으면_통계는_0_과_빈_목록이다() {
        when(participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(SEASON_ID))
                .thenReturn(List.of());
        when(orderRepository.countBuysByStock(SEASON_ID)).thenReturn(List.of());
        when(orderRepository.countOrdersByReason(SEASON_ID)).thenReturn(List.of());
        when(orderRepository.findSellOrdersBySeason(SEASON_ID)).thenReturn(List.of());
        when(stockRepository.findAllById(any())).thenReturn(List.of());

        SeasonStatisticsDTO stats = service.findStatistics(SEASON_ID);

        assertThat(stats.avgReturnRate()).isEqualByComparingTo("0");
        assertThat(stats.topBought()).isEmpty();
        assertThat(stats.reasonRatio()).isEmpty();
        assertThat(stats.panicSellRate()).isEqualByComparingTo("0");
        assertRejected(() -> service.findStatistics(99L), CommonErrorCode.NOT_FOUND);
    }

    private static TradeOrderRepository.StockOrderCount stockCount(Long stockId, long count) {
        TradeOrderRepository.StockOrderCount row = mock(TradeOrderRepository.StockOrderCount.class);
        when(row.getStockId()).thenReturn(stockId);
        when(row.getOrderCount()).thenReturn(count);
        return row;
    }

    private static TradeOrderRepository.ReasonOrderCount reasonCount(Long reasonId, long count) {
        TradeOrderRepository.ReasonOrderCount row = mock(TradeOrderRepository.ReasonOrderCount.class);
        when(row.getReasonId()).thenReturn(reasonId);
        when(row.getOrderCount()).thenReturn(count);
        return row;
    }
}
