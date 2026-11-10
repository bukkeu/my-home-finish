package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeOrderRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradeReasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#45)
*   AdminParticipantService 확인 (DB 없이 Repository·다른 Service 는 가짜(mock))
*   - 참가자 목록 : 현재 순자산·수익률 계산, 정렬, 쪽 자르기, 쪽에 있는 회원만 레벨 조회
*   - 참가자 상세 : 보유 종목(현재 가격 평가)·거래 내역·매매 이유
*   - 랭킹 미리보기 : 순자산 / 수익률 순위(동점은 같은 순위), 수상·내 순위 없음
*  */
class AdminParticipantServiceTest {

    private static final long SEASON_ID = 7L;

    private TradingSeasonRepository seasonRepository;
    private SeasonParticipantRepository participantRepository;
    private HoldingRepository holdingRepository;
    private TradeOrderRepository orderRepository;
    private TradeReasonRepository reasonRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private MemberService memberService;
    private GrowthService growthService;
    private AdminParticipantService service;
    private TradingSeason season;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        holdingRepository = mock(HoldingRepository.class);
        orderRepository = mock(TradeOrderRepository.class);
        reasonRepository = mock(TradeReasonRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        memberService = mock(MemberService.class);
        growthService = mock(GrowthService.class);
        service = new AdminParticipantService(seasonRepository, participantRepository, holdingRepository,
                orderRepository, reasonRepository, stockRepository,
                new PortfolioValuer(holdingRepository, priceRepository), memberService, growthService);

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        for (int i = 0; i < 5; i++) {
            season.advanceTurn();
        }
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 5)).thenReturn(List.of(
                StockPrice.create(SEASON_ID, 1L, 5, 100_000, new BigDecimal("1.00"))));
        when(growthService.getLevel(anyLong())).thenReturn(8);
        when(memberService.findNicknames(any())).thenReturn(Map.of(10L, "민지", 20L, "초록개미", 30L, "파랑고래"));
    }

    /** 참가자 번호 = 회원 번호 / 10. 현금과 투자 금액을 정한다 */
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

    /** 1번 민지: 현금 500,000 + 삼성 5주(현재 100,000) = 1,000,000 / 2번: 현금 2,000,000 / 3번: 현금 800,000 (투자 1,000,000) */
    private List<SeasonParticipant> givenThreeParticipants() {
        List<SeasonParticipant> participants = List.of(participant(1L, 900_000, 500_000),
                participant(2L, 1_000_000, 2_000_000), participant(3L, 1_000_000, 800_000));
        when(participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(SEASON_ID))
                .thenReturn(participants);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(holding(1L, 1L, 5, 90_000)));
        return participants;
    }

    @Test
    void 참가자_목록은_현재_순자산_큰_순서가_기본이고_수익률을_계산한다() {
        givenThreeParticipants();

        AdminParticipantListDTO list = service.findParticipants(SEASON_ID, null, 0, 50);

        assertThat(list.totalCount()).isEqualTo(3);
        assertThat(list.items()).extracting(AdminParticipantListDTO.Item::participantId).containsExactly(2L, 1L, 3L);
        AdminParticipantListDTO.Item top = list.items().get(0);
        assertThat(top.nickname()).isEqualTo("초록개미");
        assertThat(top.level()).isEqualTo(8);
        assertThat(top.netAsset()).isEqualTo(2_000_000);
        assertThat(top.returnRate()).isEqualByComparingTo("100.00");
        // 1번: 500,000 + 5 x 100,000 = 1,000,000, 투자 900,000 → 11.11%
        assertThat(list.items().get(1).netAsset()).isEqualTo(1_000_000);
        assertThat(list.items().get(1).returnRate()).isEqualByComparingTo("11.11");
    }

    @Test
    void 수익률_투자금액_참가순으로도_정렬한다() {
        givenThreeParticipants();

        assertThat(service.findParticipants(SEASON_ID, "returnRate", 0, 50).items())
                .extracting(AdminParticipantListDTO.Item::participantId).containsExactly(2L, 1L, 3L);
        assertThat(service.findParticipants(SEASON_ID, "investAmount", 0, 50).items())
                .extracting(AdminParticipantListDTO.Item::participantId).containsExactly(2L, 3L, 1L);
        assertThat(service.findParticipants(SEASON_ID, "joinedAt", 0, 50).items())
                .extracting(AdminParticipantListDTO.Item::participantId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 쪽을_자르고_그_쪽의_회원만_레벨을_조회한다() {
        givenThreeParticipants();

        AdminParticipantListDTO second = service.findParticipants(SEASON_ID, "netAsset", 1, 2);

        assertThat(second.totalCount()).isEqualTo(3);
        assertThat(second.items()).extracting(AdminParticipantListDTO.Item::participantId).containsExactly(3L);
        verify(growthService, times(1)).getLevel(anyLong());
    }

    @Test
    void 잘못된_정렬_쪽_크기는_INVALID_INPUT_이고_시즌을_읽지_않는다() {
        for (Object[] bad : new Object[][] {{"nickname", 0, 50}, {"netAsset", -1, 50}, {"netAsset", 0, 0},
                {"netAsset", 0, 201}}) {
            assertThatThrownBy(() -> service.findParticipants(SEASON_ID, (String) bad[0], (int) bad[1], (int) bad[2]))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
    }

    @Test
    void 없는_시즌은_NOT_FOUND() {
        assertThatThrownBy(() -> service.findParticipants(99L, null, 0, 50))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 참가자_상세는_보유_종목_거래_내역_매매_이유를_보여_준다() {
        SeasonParticipant participant = participant(1L, 900_000, 500_000);
        when(participantRepository.findById(1L)).thenReturn(Optional.of(participant));
        Holding held = holding(1L, 1L, 5, 90_000);
        Holding sold = holding(1L, 2L, 2, 50_000);
        sold.subtractSell(2);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L))).thenReturn(List.of(held, sold));
        when(holdingRepository.findBySeasonParticipantIdOrderByHoldingIdAsc(1L)).thenReturn(List.of(held, sold));
        TradeOrder buy = TradeOrder.createBuy(1L, 1L, 3, 5, 90_000, 4L);
        ReflectionTestUtils.setField(buy, "tradeOrderId", 901L);
        TradeOrder sell = TradeOrder.createSell(1L, 2L, 4, 2, 55_000, null);
        ReflectionTestUtils.setField(sell, "tradeOrderId", 902L);
        when(orderRepository.findBySeasonParticipantIdOrderByTradeOrderIdDesc(1L)).thenReturn(List.of(sell, buy));
        Stock samsung = Stock.create(1L, "한빛반도체", 80_000);
        ReflectionTestUtils.setField(samsung, "stockId", 1L);
        Stock mobility = Stock.create(1L, "모아모빌리티", 50_000);
        ReflectionTestUtils.setField(mobility, "stockId", 2L);
        when(stockRepository.findAllById(any())).thenReturn(List.of(samsung, mobility));
        TradeReason reason = TradeReason.create("뉴스를 보고", 1);
        ReflectionTestUtils.setField(reason, "tradeReasonId", 4L);
        when(reasonRepository.findAllById(any())).thenReturn(List.of(reason));

        AdminParticipantDetailDTO detail = service.getParticipant(1L);

        assertThat(detail.nickname()).isEqualTo("민지");
        assertThat(detail.level()).isEqualTo(8);
        assertThat(detail.cash()).isEqualTo(500_000);
        assertThat(detail.netAsset()).isEqualTo(1_000_000);
        assertThat(detail.holdings()).hasSize(1);   // 다 판 종목은 빠진다
        assertThat(detail.holdings().get(0).stockName()).isEqualTo("한빛반도체");
        assertThat(detail.holdings().get(0).currentPrice()).isEqualTo(100_000);
        assertThat(detail.holdings().get(0).evalAmount()).isEqualTo(500_000);
        assertThat(detail.orders()).extracting(AdminParticipantDetailDTO.OrderItem::orderId).containsExactly(902L, 901L);
        assertThat(detail.orders().get(0).reason()).isNull();
        assertThat(detail.orders().get(1).reason()).isEqualTo("뉴스를 보고");
        assertThat(detail.orders().get(1).stockName()).isEqualTo("한빛반도체");
    }

    @Test
    void 없는_참가자는_NOT_FOUND() {
        when(participantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getParticipant(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 랭킹_미리보기는_현재_순자산_순위이고_동점은_같은_순위다() {
        List<SeasonParticipant> participants = List.of(participant(1L, 1_000_000, 500_000),
                participant(2L, 1_000_000, 2_000_000), participant(3L, 1_000_000, 2_000_000));
        when(participantRepository.findByTradingSeasonIdOrderBySeasonParticipantIdAsc(SEASON_ID))
                .thenReturn(participants);
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of());

        RankingDTO preview = service.previewRankings(SEASON_ID, null, 0, 10);

        assertThat(preview.type()).isEqualTo("ASSET");
        assertThat(preview.totalCount()).isEqualTo(3);
        assertThat(preview.items()).extracting(RankingDTO.Item::rank).containsExactly(1, 1, 3);
        assertThat(preview.items()).extracting(RankingDTO.Item::netAsset).containsExactly(2_000_000L, 2_000_000L, 500_000L);
        assertThat(preview.items()).allMatch(item -> !item.hasBadge() && item.title() == null);
        assertThat(preview.me()).isNull();
    }

    @Test
    void 수익률_미리보기는_수익률_순서이고_쪽을_자른다() {
        // 보유 종목이 없으면 1번 -44.44%, 2번 +100%, 3번 -20%
        givenThreeParticipants();
        when(holdingRepository.findBySeasonParticipantIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of());

        RankingDTO preview = service.previewRankings(SEASON_ID, "RETURN", 0, 2);

        assertThat(preview.type()).isEqualTo("RETURN");
        assertThat(preview.items()).hasSize(2);
        assertThat(preview.items()).extracting(RankingDTO.Item::returnRate)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("-20.00"));
    }

    @Test
    void 미리보기의_잘못된_type_과_너무_큰_size_는_INVALID_INPUT() {
        for (Object[] bad : new Object[][] {{"PROFIT", 10}, {"ASSET", 51}}) {
            assertThatThrownBy(() -> service.previewRankings(SEASON_ID, (String) bad[0], 0, (int) bad[1]))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
        verify(participantRepository, never()).findByTradingSeasonIdOrderBySeasonParticipantIdAsc(anyLong());
    }
}
