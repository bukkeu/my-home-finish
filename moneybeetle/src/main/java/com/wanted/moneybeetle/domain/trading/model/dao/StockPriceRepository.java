package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 stock_price
*  */
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    /** 그 시즌·턴의 모든 종목 가격 (#24 시세) */
    List<StockPrice> findByTradingSeasonIdAndTurnNo(Long tradingSeasonId, int turnNo);
}
