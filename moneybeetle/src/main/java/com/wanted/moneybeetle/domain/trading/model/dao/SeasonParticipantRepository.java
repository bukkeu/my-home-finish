package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 season_participant
*  */
public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {

    /** 회원의 그 시즌 참가 기록 (#24 내 보유) */
    Optional<SeasonParticipant> findByTradingSeasonIdAndMemberId(Long tradingSeasonId, Long memberId);
}
