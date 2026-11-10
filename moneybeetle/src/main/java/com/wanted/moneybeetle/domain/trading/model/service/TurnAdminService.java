package com.wanted.moneybeetle.domain.trading.model.service;

import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonTurnRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnCheckDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnAdvancedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnCalendarDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : PM (모의투자)
*   #44 관리자 턴 관리 (API 118~120, ADM-13~15, SP-59)
*   - 턴 달력 : 12턴의 날짜·상태(DONE/CURRENT/UPCOMING)·넘긴 관리자와 시각, 휴장일(일요일)
*   - 다음 턴 점검 : TradingService.checkNextTurn 을 그대로 쓴다 (대시보드 요약 #193 과 같은 기준)
*   - 다음 턴 진행 : 시즌 행을 잠그고, 화면이 본 현재 턴이 다르면 TURN_CONFLICT, 휴장·마지막 턴·점검 실패면 거절.
*     넘기면 그 턴에 누가 언제 넘겼는지 남기고 admin_log(TURN_ADVANCE)에도 기록한다 (같은 트랜잭션)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TurnAdminService {

    static final String ACTION_TURN_ADVANCE = "TURN_ADVANCE";
    static final String TARGET_TYPE_SEASON = "SEASON";

    private final TradingSeasonRepository seasonRepository;
    private final SeasonTurnRepository turnRepository;
    private final TradingService tradingService;
    private final MemberService memberService;
    private final AdminLogService adminLogService;
    private final Clock clock;

    /** API 118 턴 달력·진행 기록 */
    public TurnCalendarDTO findTurns(Long seasonId) {
        TradingSeason season = getSeason(seasonId);
        List<SeasonTurn> turns = turnRepository.findByTradingSeasonIdOrderByTurnNoAsc(seasonId);
        Map<Long, String> adminNames = memberService.findNicknames(turns.stream()
                .map(SeasonTurn::getProgressedBy).filter(Objects::nonNull).distinct().toList());
        List<TurnDTO> items = turns.stream()
                .map(turn -> TurnDTO.of(turn, season.getCurrentTurn(), turn.getProgressedBy() == null ? null : adminNames.get(turn.getProgressedBy())))
                .toList();
        return new TurnCalendarDTO(seasonId, season.getStatus(), season.getCurrentTurn(), season.getTotalTurns(),
                items, holidays(season));
    }

    /** API 119 다음 턴 점검 */
    public NextTurnCheckDTO checkNextTurn(Long seasonId) {
        return tradingService.checkNextTurn(getSeason(seasonId));
    }

    /** API 120 다음 턴 진행 (되돌릴 수 없다) */
    @Transactional
    public TurnAdvancedDTO advanceTurn(Long seasonId, int expectedCurrentTurn, Long adminId) {
        if (adminId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        TradingSeason season = seasonRepository.findByIdForUpdate(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (season.getCurrentTurn() != expectedCurrentTurn) {
            throw new BusinessException(TradingErrorCode.TURN_CONFLICT);
        }
        season.checkAdvanceable();
        NextTurnCheckDTO check = tradingService.checkNextTurn(season);
        if (check.isHoliday()) {
            // 상태가 HOLIDAY 가 아니어도 일요일이면 잠근다. 되돌릴 수 없는 작업이라 점검 실패보다 먼저 알려 준다
            throw new BusinessException(TradingErrorCode.MARKET_HOLIDAY);
        }
        if (!check.ready()) {
            throw new BusinessException(TradingErrorCode.NEXT_TURN_NOT_READY);
        }

        season.advanceTurn();
        SeasonTurn turn = turnRepository.findByTradingSeasonIdAndTurnNo(seasonId, season.getCurrentTurn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        turn.recordProgress(adminId, LocalDateTime.now(clock));
        adminLogService.record(adminId, ACTION_TURN_ADVANCE, TARGET_TYPE_SEASON, seasonId,
                "{\"before\":" + expectedCurrentTurn + ",\"after\":" + season.getCurrentTurn() + "}");
        return new TurnAdvancedDTO(season.getCurrentTurn());
    }

    /** 시즌 기간 중 휴장일 (일요일) */
    private List<LocalDate> holidays(TradingSeason season) {
        List<LocalDate> holidays = new ArrayList<>();
        for (LocalDate date = season.getStartDate(); !date.isAfter(season.getEndDate()); date = date.plusDays(1)) {
            if (TradingSeason.isClosedDay(date)) {
                holidays.add(date);
            }
        }
        return holidays;
    }

    private TradingSeason getSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
