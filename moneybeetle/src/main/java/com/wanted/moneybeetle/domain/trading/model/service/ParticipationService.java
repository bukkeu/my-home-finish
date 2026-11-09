package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 홈·시즌 참가 (#23, API 53~54, INV-01~05, INV-14, SP-51~52, SP-61, DP-40)
*   - 홈 : 지금 보이는 시즌(진행 중·휴장 중·정산 중·종료)과 14일 턴 달력, 내 참가(현금·순자산·수익률)와 보유 종목
*     순자산 = 현금 + 보유 종목 평가금액(현재 턴 가격, 가격이 없으면 평균 단가), 수익률 = (순자산 - 투자 가능 금액) / 투자 가능 금액
*   - 참가 : Lv.8 이상(LevelCheckInterceptor), 시즌당 1회(DB UNIQUE 도 함께), 시즌 시작 후 첫 턴이 끝나기 전까지(SP-51 초안)
*     투자 가능 금액 = 참가할 때의 캐시 잔액 + 지난 시즌 이월금. 참가해도 회원 캐시는 빼지 않는다 (PM 결정, 이슈 #23)
*  */

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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationService {

    /**
     * 사용자에게 보이는 시즌 상태 : 진행 중, 일요일 휴장 중, 정산 중, 종료.
     * 정산 중·종료에도 홈은 열리고(season.status 로 화면이 "정산 중"·"종료" 안내를 띄운다), 참가만 JOIN_CLOSED 로 막힌다
     * 준비 중(READY) 시즌은 보이지 않고, 새 시즌이 진행 중이 되면 시작일이 더 늦은 그 시즌이 보인다
     */
    private static final List<String> VISIBLE_STATUSES = List.of(TradingSeason.STATUS_RUNNING,
            TradingSeason.STATUS_HOLIDAY, TradingSeason.STATUS_SETTLING, TradingSeason.STATUS_ENDED);
    /** 참가할 수 있는 마지막 턴 : 첫 턴이 끝나기 전까지 (SP-51 초안) */
    static final int LAST_JOIN_TURN = 1;
    /** schema.sql 의 (시즌, 회원) UNIQUE 제약 이름 */
    private static final String PARTICIPANT_KEY = "uk_season_participant_1";

    private final TradingSeasonRepository seasonRepository;
    private final SeasonTurnRepository turnRepository;
    private final SeasonParticipantRepository participantRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final RewardService rewardService;

    /** API 53 모의투자 홈. memberId 가 없거나 참가하지 않았으면 participation 은 null, holdings 는 빈 목록 */
    public TradingHomeDTO getHome(Long memberId) {
        TradingSeason season = seasonRepository.findFirstByStatusInOrderByStartDateDesc(VISIBLE_STATUSES)
                .orElseThrow(() -> new BusinessException(TradingErrorCode.NO_RUNNING_SEASON));
        TradingHomeDTO.Season seasonView = toSeasonView(season,
                turnRepository.findByTradingSeasonIdOrderByTurnNoAsc(season.getTradingSeasonId()));

        SeasonParticipant participant = memberId == null ? null
                : participantRepository.findByTradingSeasonIdAndMemberId(season.getTradingSeasonId(), memberId)
                        .orElse(null);
        if (participant == null) {
            return TradingHomeDTO.of(seasonView, null, List.of());
        }

        List<Holding> holdings = holdingRepository.findBySeasonParticipantIdAndQuantityGreaterThanOrderByStockIdAsc(
                participant.getSeasonParticipantId(), 0);
        Map<Long, Integer> prices = priceRepository
                .findByTradingSeasonIdAndTurnNo(season.getTradingSeasonId(), season.getCurrentTurn()).stream()
                .collect(Collectors.toMap(StockPrice::getStockId, StockPrice::getPrice));
        Map<Long, String> stockNames = stockRepository.findAllById(holdings.stream().map(Holding::getStockId).toList())
                .stream()
                .collect(Collectors.toMap(Stock::getStockId, Stock::getName));

        List<TradingHomeDTO.HoldingItem> items = holdings.stream()
                .map(holding -> {
                    int price = prices.getOrDefault(holding.getStockId(), holding.getAvgPrice());
                    return new TradingHomeDTO.HoldingItem(holding.getStockId(), stockNames.get(holding.getStockId()),
                            holding.getQuantity(), holding.getAvgPrice(), (long) holding.getQuantity() * price,
                            rate(price - holding.getAvgPrice(), holding.getAvgPrice()));
                })
                .toList();
        long netAsset = participant.getCash() + items.stream().mapToLong(TradingHomeDTO.HoldingItem::getEvaluation).sum();

        return TradingHomeDTO.of(seasonView,
                new TradingHomeDTO.Participation(participant.getSeasonParticipantId(), participant.getInvestAmount(),
                        participant.getCash(), netAsset,
                        rate(netAsset - participant.getInvestAmount(), participant.getInvestAmount())),
                items);
    }

    /** API 54 시즌 참가 : 투자 가능 금액 = 지금 캐시 잔액 + 지난 시즌 이월금으로 확정 (이후 바뀌지 않음, SP-52) */
    @Transactional
    public ParticipationResultDTO join(Long seasonId, Long memberId) {
        if (memberId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        TradingSeason season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!season.isRunning() || season.getCurrentTurn() > LAST_JOIN_TURN) {
            throw new BusinessException(TradingErrorCode.JOIN_CLOSED);
        }
        if (participantRepository.existsByTradingSeasonIdAndMemberId(seasonId, memberId)) {
            throw new BusinessException(TradingErrorCode.ALREADY_JOINED);
        }

        long savedCash = rewardService.getBalance(memberId);
        long carryoverIn = participantRepository
                .findFirstByMemberIdAndTradingSeasonIdNotOrderByJoinedAtDesc(memberId, seasonId)
                .map(SeasonParticipant::getCarryoverOut)
                .orElse(0L);
        try {
            SeasonParticipant participant = participantRepository.saveAndFlush(
                    SeasonParticipant.create(seasonId, memberId, savedCash, carryoverIn));
            return ParticipationResultDTO.from(participant);
        } catch (DataIntegrityViolationException e) {
            // 같은 회원이 동시에 두 번 눌러 둘 다 확인을 통과한 경우 DB UNIQUE 가 막는다
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.toLowerCase().contains(PARTICIPANT_KEY)) {
                throw new BusinessException(TradingErrorCode.ALREADY_JOINED);
            }
            throw e;
        }
    }

    /** 14일 달력 : 거래일은 턴 번호와 상태, 거래일이 아닌 날(일요일)은 휴장 */
    private static TradingHomeDTO.Season toSeasonView(TradingSeason season, List<SeasonTurn> turns) {
        Map<LocalDate, SeasonTurn> turnByDate = turns.stream()
                .collect(Collectors.toMap(SeasonTurn::getTradeDate, Function.identity()));
        List<TradingHomeDTO.CalendarDay> calendar = new ArrayList<>();
        for (LocalDate date = season.getStartDate(); !date.isAfter(season.getEndDate()); date = date.plusDays(1)) {
            SeasonTurn turn = turnByDate.get(date);
            if (turn == null) {
                calendar.add(new TradingHomeDTO.CalendarDay(date, null, TradingHomeDTO.CalendarDay.HOLIDAY));
            } else {
                calendar.add(new TradingHomeDTO.CalendarDay(date, turn.getTurnNo(), stateOf(turn.getTurnNo(), season)));
            }
        }
        LocalDateTime nextTurnAt = turns.stream()
                .filter(turn -> turn.getTurnNo() == season.getCurrentTurn() + 1)
                .findFirst()
                .map(turn -> turn.getTradeDate().atStartOfDay())
                .orElse(null);
        return new TradingHomeDTO.Season(season.getTradingSeasonId(), season.getName(), season.getStatus(),
                season.getCurrentTurn(), season.getTotalTurns(), season.getEndDate(), nextTurnAt, calendar);
    }

    private static String stateOf(int turnNo, TradingSeason season) {
        if (turnNo < season.getCurrentTurn()) {
            return TradingHomeDTO.CalendarDay.DONE;
        }
        return turnNo == season.getCurrentTurn() ? TradingHomeDTO.CalendarDay.CURRENT
                : TradingHomeDTO.CalendarDay.UPCOMING;
    }

    /** 변화량 / 기준 x 100 (소수 둘째 자리 반올림). 기준이 0 이면 0 */
    private static BigDecimal rate(long change, long base) {
        if (base == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(change).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(base), 2, RoundingMode.HALF_UP);
    }
}
