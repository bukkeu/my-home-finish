package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 holding
*  */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /** 참가자의 한 종목 보유 (#24 내 보유) */
    Optional<Holding> findBySeasonParticipantIdAndStockId(Long seasonParticipantId, Long stockId);

    /** 여러 참가자의 보유 종목을 한 번에 (순자산 계산 #45, 정산 #46). 수량 0인 기록도 들어 있다 */
    List<Holding> findBySeasonParticipantIdIn(Collection<Long> seasonParticipantIds);

    /** 한 참가자의 보유 종목 (관리자 참가자 상세 #45) */
    List<Holding> findBySeasonParticipantIdOrderByHoldingIdAsc(Long seasonParticipantId);
}
