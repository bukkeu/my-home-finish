package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   테이블 season_participant
*  */
public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {

    /** 시즌 참가자 수 (관리자 시즌 목록) */
    long countByTradingSeasonId(Long tradingSeasonId);
}
