package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : PM (모의투자)
*   테이블 holding
*  */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /** 참가자의 보유 종목 (#23 홈) */
    List<Holding> findBySeasonParticipantIdAndQuantityGreaterThanOrderByStockIdAsc(Long seasonParticipantId,
            int quantity);

    /** 참가자의 한 종목 보유 (#24 내 보유) */
    Optional<Holding> findBySeasonParticipantIdAndStockId(Long seasonParticipantId, Long stockId);

    /** 주문할 때 최신 보유 수량을 읽고 같은 보유 행을 다른 주문이 동시에 바꾸지 못하게 잠근다 (#25) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.seasonParticipantId = :participantId and h.stockId = :stockId")
    Optional<Holding> findForUpdateBySeasonParticipantIdAndStockId(@Param("participantId") Long seasonParticipantId,
            @Param("stockId") Long stockId);

    /** 여러 참가자의 보유 종목을 한 번에 (순자산 계산 #45, 정산 #46). 수량 0인 기록도 들어 있다 */
    List<Holding> findBySeasonParticipantIdIn(Collection<Long> seasonParticipantIds);

    /** 한 참가자의 보유 종목 (관리자 참가자 상세 #45) */
    List<Holding> findBySeasonParticipantIdOrderByHoldingIdAsc(Long seasonParticipantId);
}
