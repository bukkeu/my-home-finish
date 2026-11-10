package com.wanted.moneybeetle.domain.trading.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonTurnRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonCreatedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSettingDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatusDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
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
*   담당 : PM (#40)
*   SeasonAdminService 확인 (DB 없이 Repository 는 가짜(mock))
*   - 생성 : 월요일 확인, 12턴 달력 저장, 이전 시즌 설정 복사
*   - 목록 : 참가자 수 포함 / 설정·상태 : 없는 시즌 404, 규칙은 엔티티가 판단
*  */
class SeasonAdminServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private TradingSeasonRepository seasonRepository;
    private SeasonTurnRepository turnRepository;
    private SeasonParticipantRepository participantRepository;
    private SeasonAdminService service;

    @BeforeEach
    void setUp() {
        seasonRepository = mock(TradingSeasonRepository.class);
        turnRepository = mock(SeasonTurnRepository.class);
        participantRepository = mock(SeasonParticipantRepository.class);
        service = new SeasonAdminService(seasonRepository, turnRepository, participantRepository);

        // 저장하면 번호가 붙는 것처럼 흉내 낸다
        when(seasonRepository.save(any(TradingSeason.class))).thenAnswer(invocation -> {
            TradingSeason season = invocation.getArgument(0);
            ReflectionTestUtils.setField(season, "tradingSeasonId", 8L);
            return season;
        });
        when(turnRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TradingSeason savedSeason(long id, String name) {
        TradingSeason season = TradingSeason.create(name, MONDAY, ADMIN_ID);
        ReflectionTestUtils.setField(season, "tradingSeasonId", id);
        return season;
    }

    @Test
    void 로그인한_관리자_번호가_없으면_시즌을_만들지_않고_UNAUTHORIZED() {
        assertThatThrownBy(() -> service.createSeason(new SeasonSaveDTO("시즌 8", MONDAY, null), null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void 시즌을_만들면_12턴_달력을_함께_만든다() {
        SeasonCreatedDTO result = service.createSeason(new SeasonSaveDTO("시즌 8", MONDAY, null), ADMIN_ID);

        assertThat(result.getSeasonId()).isEqualTo(8L);
        assertThat(result.getTurns()).hasSize(12);
        assertThat(result.getTurns().get(0).getTurnNo()).isEqualTo(1);
        assertThat(result.getTurns().get(0).getTradeDate()).isEqualTo(MONDAY);
        assertThat(result.getTurns().get(11).getTurnNo()).isEqualTo(12);
        assertThat(result.getTurns().get(11).getTradeDate()).isEqualTo(LocalDate.of(2026, 11, 28));
    }

    @Test
    void 시작일이_월요일이_아니면_START_DATE_NOT_MONDAY_이고_저장하지_않는다() {
        SeasonSaveDTO tuesday = new SeasonSaveDTO("시즌 8", MONDAY.plusDays(1), null);

        assertThatThrownBy(() -> service.createSeason(tuesday, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.START_DATE_NOT_MONDAY);
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void 이전_시즌을_고르면_이월_해금_설정을_복사한다() {
        TradingSeason previous = savedSeason(7L, "시즌 7");
        previous.changeSetting(new BigDecimal("50.00"), 300_000L, 10);
        when(seasonRepository.findById(7L)).thenReturn(Optional.of(previous));

        service.createSeason(new SeasonSaveDTO("시즌 8", MONDAY, 7L), ADMIN_ID);

        verify(seasonRepository).save(org.mockito.ArgumentMatchers.argThat(season ->
                season.getCarryoverRate().compareTo(new BigDecimal("50.00")) == 0
                        && Long.valueOf(300_000L).equals(season.getCarryoverCap())
                        && season.getUnlockLevel() == 10
                        && "시즌 8".equals(season.getName())));
    }

    @Test
    void 복사할_시즌이_없으면_NOT_FOUND() {
        when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSeason(new SeasonSaveDTO("시즌 8", MONDAY, 99L), ADMIN_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 시즌_목록에는_참가자_수가_들어가고_참가자가_없는_시즌은_0이다() {
        when(seasonRepository.findAllByOrderByStartDateDesc())
                .thenReturn(List.of(savedSeason(7L, "시즌 7"), savedSeason(8L, "시즌 8")));
        when(participantRepository.countByTradingSeasonIds(List.of(7L, 8L)))
                .thenReturn(List.of(participantCount(7L, 1240L)));

        List<SeasonSummaryDTO> seasons = service.findSeasons();

        assertThat(seasons).hasSize(2);
        assertThat(seasons.get(0).getSeasonId()).isEqualTo(7L);
        assertThat(seasons.get(0).getStatus()).isEqualTo("READY");
        assertThat(seasons.get(0).getParticipants()).isEqualTo(1240L);
        assertThat(seasons.get(1).getParticipants()).isZero();
        // 시즌마다 세지 않고 한 번에 센다 (N+1 방지)
        verify(participantRepository, never()).countByTradingSeasonId(any());
    }

    @Test
    void 시즌이_없으면_참가자_수를_조회하지_않는다() {
        when(seasonRepository.findAllByOrderByStartDateDesc()).thenReturn(List.of());

        assertThat(service.findSeasons()).isEmpty();
        verify(participantRepository, never()).countByTradingSeasonIds(any());
    }

    private static SeasonParticipantRepository.SeasonParticipantCount participantCount(long seasonId, long count) {
        return new SeasonParticipantRepository.SeasonParticipantCount() {
            @Override
            public Long getSeasonId() {
                return seasonId;
            }

            @Override
            public long getParticipantCount() {
                return count;
            }
        };
    }

    @Test
    void 설정과_상태를_바꾼다() {
        TradingSeason season = savedSeason(7L, "시즌 7");
        when(seasonRepository.findById(7L)).thenReturn(Optional.of(season));
        when(seasonRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(season));

        service.changeSetting(7L, new SeasonSettingDTO(new BigDecimal("80.00"), null, 9));
        service.changeStatus(7L, new SeasonStatusDTO("RUNNING"));

        assertThat(season.getCarryoverRate()).isEqualByComparingTo("80.00");
        assertThat(season.getUnlockLevel()).isEqualTo(9);
        assertThat(season.isRunning()).isTrue();
    }

    @Test
    void 정산_중인_시즌의_설정을_바꾸면_SEASON_CLOSED() {
        TradingSeason season = savedSeason(7L, "시즌 7");
        season.changeStatus(TradingSeason.STATUS_RUNNING);
        season.changeStatus(TradingSeason.STATUS_SETTLING);
        when(seasonRepository.findById(7L)).thenReturn(Optional.of(season));

        assertThatThrownBy(() -> service.changeSetting(7L,
                new SeasonSettingDTO(new BigDecimal("80.00"), null, 9)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.SEASON_CLOSED);
    }

    @Test
    void 없는_시즌의_상태를_바꾸면_NOT_FOUND() {
        when(seasonRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(99L, new SeasonStatusDTO("RUNNING")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
