package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trade_reason
*  */
public interface TradeReasonRepository extends JpaRepository<TradeReason, Long> {

}
