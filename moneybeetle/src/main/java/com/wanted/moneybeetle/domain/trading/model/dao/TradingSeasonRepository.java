package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 trading_season
*  */
public interface TradingSeasonRepository extends JpaRepository<TradingSeason, Long> {

}
