package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradeOrder;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** 시즌 안에서 주어진 시각 범위에 들어온 주문 수 (관리자 대시보드의 오늘 주문 수, #193) */
    @Query("""
            select count(o)
            from TradeOrder o, SeasonParticipant p
            where o.seasonParticipantId = p.seasonParticipantId and p.tradingSeasonId = :seasonId
              and o.createdAt >= :from and o.createdAt < :to
            """)
    long countOrdersBetween(@Param("seasonId") Long tradingSeasonId, @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    /** 내 거래 내역 한 쪽. 정렬은 Pageable 로 (최근 거래부터) (#25) */
    Page<TradeOrder> findBySeasonParticipantId(Long seasonParticipantId, Pageable pageable);
}
