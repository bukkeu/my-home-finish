package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.StockPrice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 stock_price
*  */
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    /** 시즌의 가격표 전체 (30종목 x 12턴) */
    List<StockPrice> findAllByTradingSeasonId(Long tradingSeasonId);
}
