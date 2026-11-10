package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonTurnRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnCheckDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnAdvancedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnCalendarDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM (#44)
*   TurnAdminService 확인 (DB 없이 Repository·다른 Service 는 가짜(mock))
*   - 턴 달력 : 지난 턴/지금 턴/남은 턴, 넘긴 관리자 닉네임, 휴장일(일요일)
*   - 다음 턴 진행 : 점검 통과 시 턴·진행 기록·관리자 로그, 현재 턴 불일치·휴장·마지막 턴·점검 실패 거절
*  */
class TurnAdminServiceTest {

    private static final long SEASON_ID = 7L;
    private static final long ADMIN_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("Asia/Seoul"));

    private TradingSeasonRepository seasonRepository;
    private SeasonTurnRepository turnRepository;
    private TradingService tradingService;
    private MemberService memberService;
    private AdminLogService adminLogService;
    private TurnAdminService service;
    private TradingSeason season;
    private List<SeasonTurn> turns;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        turnRepository = mock(SeasonTurnRepository.class);
        tradingService = mock(TradingService.class);
        memberService = mock(MemberService.class);
        adminLogService = mock(AdminLogService.class);
        service = new TurnAdminService(seasonRepository, turnRepository, tradingService, memberService,
                adminLogService, CLOCK);

        season = TradingSeason.create("시즌 7", LocalDate.of(2026, 11, 2), 1L);
        ReflectionTestUtils.setField(season, "tradingSeasonId", SEASON_ID);
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        turns = new ArrayList<>();
        List<LocalDate> dates = season.tradeDates();
        for (int i = 0; i < dates.size(); i++) {
            turns.add(SeasonTurn.create(SEASON_ID, i + 1, dates.get(i)));
        }
        when(seasonRepository.findById(SEASON_ID)).thenReturn(Optional.of(season));
        when(seasonRepository.findByIdForUpdate(SEASON_ID)).thenReturn(Optional.of(season));
        when(turnRepository.findByTradingSeasonIdOrderByTurnNoAsc(SEASON_ID)).thenReturn(turns);
        when(turnRepository.findByTradingSeasonIdAndTurnNo(eq(SEASON_ID), anyInt())).thenAnswer(
                invocation -> Optional.of(turns.get((int) invocation.getArgument(1) - 1)));
        when(memberService.findNicknames(any())).thenReturn(Map.of());
    }

    private void givenCheck(boolean ready) {
        when(tradingService.checkNextTurn(season))
                .thenReturn(new NextTurnCheckDTO(ready, season.getCurrentTurn() + 1, List.of(), 1, false, true));
    }

    @Test
    void 턴_달력은_지난_턴_지금_턴_남은_턴과_넘긴_관리자를_보여_준다() {
        for (int i = 0; i < 3; i++) {
            season.advanceTurn();
        }
        turns.get(1).recordProgress(ADMIN_ID, LocalDateTime.of(2026, 11, 3, 0, 0, 3));
        when(memberService.findNicknames(any())).thenReturn(Map.of(ADMIN_ID, "김운영"));

        TurnCalendarDTO calendar = service.findTurns(SEASON_ID);

        assertThat(calendar.currentTurn()).isEqualTo(3);
        assertThat(calendar.turns()).hasSize(12);
        assertThat(calendar.turns().get(0).state()).isEqualTo("DONE");
        assertThat(calendar.turns().get(1).state()).isEqualTo("DONE");
        assertThat(calendar.turns().get(1).progressedBy()).isEqualTo("김운영");
        assertThat(calendar.turns().get(1).progressedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 0, 0, 3));
        assertThat(calendar.turns().get(2).state()).isEqualTo("CURRENT");
        assertThat(calendar.turns().get(3).state()).isEqualTo("UPCOMING");
        assertThat(calendar.turns().get(3).progressedBy()).isNull();
        assertThat(calendar.holidays()).containsExactly(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15));
    }

    @Test
    void 없는_시즌의_달력은_NOT_FOUND() {
        assertThatThrownBy(() -> service.findTurns(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 점검을_통과하면_다음_턴으로_넘기고_진행_기록과_관리자_로그를_남긴다() {
        givenCheck(true);

        TurnAdvancedDTO result = service.advanceTurn(SEASON_ID, 0, ADMIN_ID);

        assertThat(result.currentTurn()).isEqualTo(1);
        assertThat(season.getCurrentTurn()).isEqualTo(1);
        assertThat(turns.get(0).getProgressedBy()).isEqualTo(ADMIN_ID);
        assertThat(turns.get(0).getProgressedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 0, 0));
        verify(adminLogService).record(ADMIN_ID, "TURN_ADVANCE", "SEASON", SEASON_ID, "{\"before\":0,\"after\":1}");
    }

    @Test
    void 화면이_본_현재_턴이_다르면_TURN_CONFLICT_이고_넘기지_않는다() {
        givenCheck(true);

        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 3, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.TURN_CONFLICT));
        assertThat(season.getCurrentTurn()).isZero();
        verify(adminLogService, never()).record(anyLong(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void 점검에_실패하면_NEXT_TURN_NOT_READY_이고_넘기지_않는다() {
        givenCheck(false);

        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 0, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.NEXT_TURN_NOT_READY));
        assertThat(season.getCurrentTurn()).isZero();
        assertThat(turns.get(0).isProgressed()).isFalse();
    }

    @Test
    void 휴장일에는_점검을_보기_전에_MARKET_HOLIDAY_로_거절한다() {
        season.changeStatus(TradingSeason.STATUS_HOLIDAY);

        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 0, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_HOLIDAY));
        verify(tradingService, never()).checkNextTurn(any());
    }

    @Test
    void 마지막_턴_이후에는_LAST_TURN_REACHED() {
        givenCheck(true);
        for (int i = 0; i < 12; i++) {
            season.advanceTurn();
        }

        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 12, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.LAST_TURN_REACHED));
    }

    @Test
    void 로그인한_관리자가_없으면_UNAUTHORIZED() {
        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    void 다음_턴_점검은_TradingService_의_계산을_그대로_돌려준다() {
        NextTurnCheckDTO check = new NextTurnCheckDTO(false, 1, List.of("모아모빌리티"), 0, false, true);
        when(tradingService.checkNextTurn(season)).thenReturn(check);

        assertThat(service.checkNextTurn(SEASON_ID)).isSameAs(check);
    }

    @Test
    void 이전_시즌_정산_전에는_점검보다_먼저_PREVIOUS_SEASON_NOT_SETTLED_로_거절한다() {
        when(tradingService.checkNextTurn(season))
                .thenReturn(new NextTurnCheckDTO(false, 1, List.of("모아모빌리티"), 0, false, false));

        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 0, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.PREVIOUS_SEASON_NOT_SETTLED));
        assertThat(season.getCurrentTurn()).isZero();
        verify(adminLogService, never()).record(anyLong(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void 일요일_휴장은_시즌_상태가_진행_중이어도_MARKET_HOLIDAY_로_거절한다() {
        when(tradingService.checkNextTurn(season))
                .thenReturn(new NextTurnCheckDTO(false, 1, List.of(), 1, true, true));

        assertThatThrownBy(() -> service.advanceTurn(SEASON_ID, 0, ADMIN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_HOLIDAY));
        assertThat(season.isRunning()).isTrue();
        assertThat(season.getCurrentTurn()).isZero();
        verify(adminLogService, never()).record(anyLong(), anyString(), anyString(), anyLong(), any());
    }
}
