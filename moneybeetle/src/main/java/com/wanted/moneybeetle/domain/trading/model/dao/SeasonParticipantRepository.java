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

    /** 이미 참가했는지 (#23) */
    boolean existsByTradingSeasonIdAndMemberId(Long tradingSeasonId, Long memberId);

    /** 이 시즌을 뺀 가장 최근 참가 기록 : 이월금(carryover_out)을 가져온다 (#23, SP-63) */
    Optional<SeasonParticipant> findFirstByMemberIdAndTradingSeasonIdNotOrderByJoinedAtDesc(Long memberId,
            Long tradingSeasonId);
}
