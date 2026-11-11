package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonTurnRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.ParticipationResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingHomeDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import com.wanted.moneybeetle.domain.trading.model.entity.Stock;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#23)
*   ParticipationService 홈·시즌 참가 확인 (DB 없이 Repository·RewardService 는 가짜(mock))
*   - 참가 : 투자 가능 금액 = 캐시 잔액 + 지난 시즌 이월금, 시즌당 1회, 첫 턴이 끝나면 마감, 캐시는 빼지 않는다
*   - 홈 : 14일 달력(휴장일 포함), 순자산·수익률(현재 턴 가격), 참가 안 했으면 참가 정보 없음
*  */
class ParticipationServiceTest {

    private static final long SEASON_ID = 7L;
    private static final long MEMBER_ID = 8L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private TradingSeasonRepository seasonRepository;
    private SeasonTurnRepository turnRepository;
    private SeasonParticipantRepository participantRepository;
    private HoldingRepository holdingRepository;
    private StockRepository stockRepository;
    private StockPriceRepository priceRepository;
    private RewardService rewardService;
    private ParticipationService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        turnRepository = mock(SeasonTurnRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        holdingRepository = mock(HoldingRepository.class);
        stockRepository = mock(StockRepository.class);
        priceRepository = mock(StockPriceRepository.class);
        rewardService = mock(RewardService.class);
        service = new ParticipationService(seasonRepository, turnRepository, participantRepository, holdingRepository,
                stockRepository, priceRepository, rewardService);
    }

    /** 시즌 7 을 currentTurn 까지 진행시켜 돌려준다 (0 이면 준비 중) */
    private TradingSeason season(int currentTurn) {
        TradingSeason season = TradingSeason.create("시즌 7", MONDAY, new BigDecimal("100.00"), null, 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        if (currentTurn > 0) {
            season.changeStatus(TradingSeason.STATUS_RUNNING);
            for (int i = 0; i < currentTurn; i++) {
                season.advanceTurn();
            }
        }
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(seasonRepository.findByIdForUpdate(SEASON_ID)).thenReturn(Optional.of(season));
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.of(season));
        return season;
    }

    /** 일요일을 뺀 12거래일 */
    private void givenTurns() {
        List<SeasonTurn> turns = new ArrayList<>();
        LocalDate date = MONDAY;
        int turnNo = 1;
        while (turnNo <= 12) {
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                turns.add(SeasonTurn.create(SEASON_ID, turnNo++, date));
            }
            date = date.plusDays(1);
        }
        when(turnRepository.findByTradingSeasonIdOrderByTurnNoAsc(SEASON_ID)).thenReturn(turns);
    }

    @Test
    void 참가하면_캐시_잔액과_이월금으로_투자_가능_금액을_정하고_캐시는_빼지_않는다() {
        season(1);
        when(rewardService.getBalance(MEMBER_ID)).thenReturn(3_200_000L);
        SeasonParticipant previous = SeasonParticipant.create(6L, MEMBER_ID, 1_000_000L, 0L);
        previous.settle(1_620_000L, new BigDecimal("62.00"), 1_620_000L);
        when(participantRepository.findPreviousParticipation(MEMBER_ID, SEASON_ID))
                .thenReturn(Optional.of(previous));
        when(participantRepository.saveAndFlush(any(SeasonParticipant.class))).thenAnswer(invocation -> {
            SeasonParticipant saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "seasonParticipantId", 120L);
            return saved;
        });

        ParticipationResultDTO result = service.join(SEASON_ID, MEMBER_ID);

        assertThat(result.getParticipantId()).isEqualTo(120L);
        assertThat(result.getSavedCash()).isEqualTo(3_200_000L);
        assertThat(result.getCarryoverIn()).isEqualTo(1_620_000L);
        assertThat(result.getInvestAmount()).isEqualTo(4_820_000L);
        verify(seasonRepository).findByIdForUpdate(SEASON_ID);
        verify(rewardService, never()).spendCash(any(), any(), any(), anyLong());
    }

    @Test
    void 첫_참가면_이월금은_0() {
        season(1);
        when(rewardService.getBalance(MEMBER_ID)).thenReturn(500_000L);
        when(participantRepository.saveAndFlush(any(SeasonParticipant.class))).thenAnswer(i -> i.getArgument(0));

        ParticipationResultDTO result = service.join(SEASON_ID, MEMBER_ID);

        assertThat(result.getCarryoverIn()).isZero();
        assertThat(result.getInvestAmount()).isEqualTo(500_000L);
    }

    @Test
    void rejectsUnsettledPreviousSeasonWithoutCreatingParticipation() {
        season(1);
        SeasonParticipant unsettled = SeasonParticipant.create(6L, MEMBER_ID, 1_000_000L, 0L);
        when(participantRepository.findPreviousParticipation(MEMBER_ID, SEASON_ID)).thenReturn(Optional.of(unsettled));

        assertThatThrownBy(() -> service.join(SEASON_ID, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.PREVIOUS_SEASON_NOT_SETTLED);
        verify(participantRepository, never()).saveAndFlush(any());
        verifyNoInteractions(rewardService);
    }

    @Test
    void 이미_참가했으면_ALREADY_JOINED() {
        season(1);
        when(participantRepository.existsByTradingSeasonIdAndMemberId(SEASON_ID, MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.join(SEASON_ID, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.ALREADY_JOINED);
        verify(participantRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_두_번_참가해서_UNIQUE가_막아도_ALREADY_JOINED() {
        season(1);
        when(participantRepository.saveAndFlush(any(SeasonParticipant.class))).thenThrow(
                new DataIntegrityViolationException("could not execute statement",
                        new RuntimeException("Duplicate entry '7-8' for key 'season_participant.uk_season_participant_1'")));

        assertThatThrownBy(() -> service.join(SEASON_ID, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.ALREADY_JOINED);
    }

    @Test
    void 첫_턴이_끝났거나_시작_전이면_JOIN_CLOSED() {
        season(2);
        assertThatThrownBy(() -> service.join(SEASON_ID, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.JOIN_CLOSED);

        season(0);
        assertThatThrownBy(() -> service.join(SEASON_ID, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.JOIN_CLOSED);
        verifyNoInteractions(rewardService);
    }

    @Test
    void 로그인_회원이_없으면_UNAUTHORIZED_없는_시즌은_NOT_FOUND() {
        assertThatThrownBy(() -> service.join(SEASON_ID, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        when(seasonRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.join(99L, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 홈의_달력은_14일이고_일요일은_휴장_턴은_지난_현재_남은_턴으로_나뉜다() {
        season(5);
        givenTurns();

        TradingHomeDTO home = service.getHome(null);

        List<TradingHomeDTO.CalendarDay> calendar = home.getSeason().getCalendar();
        assertThat(calendar).hasSize(14);
        assertThat(calendar.get(0).getState()).isEqualTo(TradingHomeDTO.CalendarDay.DONE);
        assertThat(calendar.get(4).getTurnNo()).isEqualTo(5);
        assertThat(calendar.get(4).getState()).isEqualTo(TradingHomeDTO.CalendarDay.CURRENT);
        assertThat(calendar.get(5).getState()).isEqualTo(TradingHomeDTO.CalendarDay.UPCOMING);
        assertThat(calendar.get(6).getTurnNo()).isNull();   // 10-25 일요일
        assertThat(calendar.get(6).getState()).isEqualTo(TradingHomeDTO.CalendarDay.HOLIDAY);
        assertThat(home.getSeason().getNextTurnAt()).isEqualTo(LocalDate.of(2026, 10, 24).atTime(8, 0));
        // 로그인 회원이 없으면 참가 정보 없이 보인다
        assertThat(home.getParticipation()).isNull();
        assertThat(home.getHoldings()).isEmpty();
        assertThat(home.getNotice()).isEqualTo(TradingHomeDTO.NOTICE);
    }

    @Test
    void 홈의_순자산은_현금과_현재_턴_가격_평가금액의_합() {
        season(5);
        givenTurns();
        SeasonParticipant participant = SeasonParticipant.create(SEASON_ID, MEMBER_ID, 4_820_000L, 0L);
        ReflectionTestUtils.setField(participant, "seasonParticipantId", 120L);
        participant.payCash(948_000L);   // 12주 x 79,000
        when(participantRepository.findByTradingSeasonIdAndMemberId(SEASON_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        Holding holding = Holding.create(120L, 1L);
        holding.addBuy(12, 79_000);
        when(holdingRepository.findBySeasonParticipantIdAndQuantityGreaterThanOrderByStockIdAsc(120L, 0))
                .thenReturn(List.of(holding));
        when(priceRepository.findByTradingSeasonIdAndTurnNo(SEASON_ID, 5))
                .thenReturn(List.of(StockPrice.create(SEASON_ID, 1L, 5, 84_500, new BigDecimal("2.80"))));
        Stock stock = Stock.create(1L, "한빛반도체", 92_900);
        ReflectionTestUtils.setField(stock, "stockId", 1L);
        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));

        TradingHomeDTO home = service.getHome(MEMBER_ID);

        TradingHomeDTO.HoldingItem item = home.getHoldings().get(0);
        assertThat(item.getStockName()).isEqualTo("한빛반도체");
        assertThat(item.getEvaluation()).isEqualTo(1_014_000L);
        assertThat(item.getReturnRate()).isEqualByComparingTo("6.96");
        assertThat(home.getParticipation().getCash()).isEqualTo(3_872_000L);
        assertThat(home.getParticipation().getNetAsset()).isEqualTo(4_886_000L);
        assertThat(home.getParticipation().getReturnRate()).isEqualByComparingTo("1.37");
    }

    @Test
    void 정산_중인_시즌도_홈이_열리고_상태로_안내하며_참가는_마감() {
        TradingSeason season = season(12);
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        givenTurns();

        TradingHomeDTO home = service.getHome(null);

        assertThat(home.getSeason().getStatus()).isEqualTo(TradingSeason.STATUS_SETTLING);
        assertThat(home.getSeason().getCurrentTurn()).isEqualTo(12);
        assertThat(home.getSeason().getNextTurnAt()).isNull();
        verify(seasonRepository).findFirstByStatusInOrderByStartDateDesc(List.of(TradingSeason.STATUS_RUNNING,
                TradingSeason.STATUS_HOLIDAY, TradingSeason.STATUS_SETTLING, TradingSeason.STATUS_ENDED));
        assertThatThrownBy(() -> service.join(SEASON_ID, MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.JOIN_CLOSED);
    }

    @Test
    void 보이는_시즌이_없으면_홈은_NO_RUNNING_SEASON() {
        when(seasonRepository.findFirstByStatusInOrderByStartDateDesc(anyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHome(MEMBER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.NO_RUNNING_SEASON);
    }
}
