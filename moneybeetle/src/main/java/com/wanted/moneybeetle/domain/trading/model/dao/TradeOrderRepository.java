package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trade_order
*  */
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    /** 시즌 안에서 종목별 거래 수 (#24 인기순). 거래가 없는 종목은 결과에 없다 */
    @Query("""
            select o.stockId as stockId, count(o) as orderCount
            from TradeOrder o, SeasonParticipant p
            where o.seasonParticipantId = p.seasonParticipantId and p.tradingSeasonId = :seasonId
            group by o.stockId
            """)
    List<StockOrderCount> countOrdersByStock(@Param("seasonId") Long tradingSeasonId);

    interface StockOrderCount {
        Long getStockId();

        long getOrderCount();
    }

    /** 한 참가자의 거래 내역, 최근 거래부터 (관리자 참가자 상세 #45) */
    List<TradeOrder> findBySeasonParticipantIdOrderByTradeOrderIdDesc(Long seasonParticipantId);
}
