package com.wanted.moneybeetle.domain.trading.model.service;

import com.wanted.moneybeetle.domain.trading.model.dao.HoldingRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.StockPriceRepository;
import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : PM (모의투자)
*   참가자의 순자산·수익률·순위 계산 (SP-61, SP-62). 관리자 참가자 목록·랭킹 미리보기(#45)와 정산(#46)이 함께 쓴다
*   - 순자산 = 현금 + 보유 종목 평가금액 (수량 x 평가 턴의 가격). 평가 턴 = 현재 턴 (마지막 턴을 넘지 않는다)
*   - 수익률 = (순자산 - 투자 가능 금액) ÷ 투자 가능 금액 x 100, 소수 둘째 자리까지 (반올림)
*   - 이미 정산된 참가자는 정산 때 확정한 값을 그대로 쓴다
*   - 순위 : 값이 큰 순서, 동점은 같은 순위 (1, 2, 2, 4 ...)
*  */
@Component
@RequiredArgsConstructor
public class PortfolioValuer {

    private final HoldingRepository holdingRepository;
    private final StockPriceRepository priceRepository;

    /** 한 참가자의 평가 결과 */
    public record Valuation(long netAsset, BigDecimal returnRate) {
    }

    /** 참가자 번호 → 평가 결과. 보유 종목과 가격은 한 번에 읽는다 (참가자마다 읽지 않는다) */
    public Map<Long, Valuation> value(TradingSeason season, List<SeasonParticipant> participants) {
        if (participants.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Holding>> holdings = holdingRepository.findBySeasonParticipantIdIn(
                        participants.stream().map(SeasonParticipant::getSeasonParticipantId).toList()).stream()
                .filter(holding -> !holding.isEmpty())
                .collect(Collectors.groupingBy(Holding::getSeasonParticipantId));
        Map<Long, Integer> prices = pricesAtValuationTurn(season);

        Map<Long, Valuation> result = new HashMap<>();
        for (SeasonParticipant participant : participants) {
            if (participant.isSettled()) {
                result.put(participant.getSeasonParticipantId(),
                        new Valuation(participant.getFinalNetAsset(), participant.getReturnRate()));
                continue;
            }
            long netAsset = participant.getCash();
            for (Holding holding : holdings.getOrDefault(participant.getSeasonParticipantId(), List.of())) {
                netAsset += (long) holding.getQuantity() * priceOf(holding, prices);
            }
            result.put(participant.getSeasonParticipantId(),
                    new Valuation(netAsset, returnRate(netAsset, participant.getInvestAmount())));
        }
        return result;
    }

    /** 평가 턴의 종목별 가격. 아직 턴이 시작되지 않았으면(0턴) 비어 있다 */
    public Map<Long, Integer> pricesAtValuationTurn(TradingSeason season) {
        int turn = Math.min(season.getCurrentTurn(), season.getTotalTurns());
        if (turn < 1) {
            return Map.of();
        }
        return priceRepository.findByTradingSeasonIdAndTurnNo(season.getTradingSeasonId(), turn).stream()
                .collect(Collectors.toMap(StockPrice::getStockId, StockPrice::getPrice, (a, b) -> a));
    }

    /** 평가 턴 가격. 그 종목 가격이 없으면(입력 누락) 평균 매수 단가로 평가한다 */
    public static int priceOf(Holding holding, Map<Long, Integer> prices) {
        return prices.getOrDefault(holding.getStockId(), holding.getAvgPrice());
    }

    /** 수익률 (%) = (순자산 - 투자 가능 금액) ÷ 투자 가능 금액 x 100. 투자 가능 금액이 0 이하면 0 */
    public static BigDecimal returnRate(long netAsset, long investAmount) {
        if (investAmount <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(netAsset - investAmount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(investAmount), 2, RoundingMode.HALF_UP);
    }

    /**
     * 값이 큰 순서로 순위를 매긴다. 같은 값은 같은 순위이고 다음 순위는 건너뛴다 (1, 2, 2, 4).
     * 반환 목록은 순위 순서이고, 같은 순위 안에서는 입력 순서를 지킨다
     */
    public static <T> List<Ranked<T>> rank(List<T> items, Function<T, ? extends Comparable<?>> key) {
        @SuppressWarnings("unchecked")
        Comparator<T> byKeyDesc = (a, b) -> ((Comparable<Object>) key.apply(b)).compareTo(key.apply(a));
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(byKeyDesc);
        List<Ranked<T>> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int rank = i > 0 && byKeyDesc.compare(sorted.get(i - 1), sorted.get(i)) == 0
                    ? ranked.get(i - 1).rank() : i + 1;
            ranked.add(new Ranked<>(rank, sorted.get(i)));
        }
        return ranked;
    }

    /** 순위가 붙은 한 줄 */
    public record Ranked<T>(int rank, T item) {
    }
}
