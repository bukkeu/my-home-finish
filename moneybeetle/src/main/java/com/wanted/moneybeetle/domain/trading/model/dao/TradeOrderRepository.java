package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 trade_order
*  */
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

}
