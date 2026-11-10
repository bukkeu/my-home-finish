package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradeReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trade_reason
*  */
public interface TradeReasonRepository extends JpaRepository<TradeReason, Long> {

    /** 주문할 때 고를 수 있는 사용 중 선택지, 정렬 순서대로 (#25) */
    List<TradeReason> findByIsActiveTrueOrderBySortOrderAscTradeReasonIdAsc();
}
