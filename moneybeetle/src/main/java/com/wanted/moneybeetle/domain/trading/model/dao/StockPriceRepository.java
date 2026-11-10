package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/* comment.
*   담당 : PM (모의투자)
*   테이블 stock_price
*  */
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    /** 그 시즌·턴의 모든 종목 가격 (#24 시세) */
    List<StockPrice> findByTradingSeasonIdAndTurnNo(Long tradingSeasonId, int turnNo);

    /** 시즌의 가격표 전체 (30종목 x 12턴) */
    List<StockPrice> findAllByTradingSeasonId(Long tradingSeasonId);

    /** 한 종목의 1턴부터 주어진 턴까지 가격 흐름 (#24 종목 상세) */
    List<StockPrice> findByTradingSeasonIdAndStockIdAndTurnNoLessThanEqualOrderByTurnNoAsc(
            Long tradingSeasonId, Long stockId, int turnNo);

    /** 그 시즌·턴·종목의 가격 한 건 (주문 체결가 #25) */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<StockPrice> findByTradingSeasonIdAndStockIdAndTurnNo(Long tradingSeasonId, Long stockId, int turnNo);
}
