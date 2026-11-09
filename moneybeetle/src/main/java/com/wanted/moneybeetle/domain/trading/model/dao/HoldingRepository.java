package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 holding
*  */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /** 참가자의 보유 종목 (#23 홈) */
    List<Holding> findBySeasonParticipantIdAndQuantityGreaterThanOrderByStockIdAsc(Long seasonParticipantId,
            int quantity);
}
