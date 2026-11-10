package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** 시즌의 모든 가격 (시즌 통계의 급락 턴 판단 #46) */
    List<StockPrice> findByTradingSeasonId(Long tradingSeasonId);
}
