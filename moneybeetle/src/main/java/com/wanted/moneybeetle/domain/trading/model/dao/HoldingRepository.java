package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.Holding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 holding
*  */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /** 참가자의 한 종목 보유 (#24 내 보유) */
    Optional<Holding> findBySeasonParticipantIdAndStockId(Long seasonParticipantId, Long stockId);
}
