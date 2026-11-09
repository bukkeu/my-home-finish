package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : PM (모의투자)
*   테이블 season_participant
*  */
public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {

    /** 시즌 참가자 수 (관리자 시즌 목록) */
    long countByTradingSeasonId(Long tradingSeasonId);

    /** 여러 시즌의 참가자 수를 한 번에 센다 (관리자 시즌 목록 N+1 방지). 참가자가 없는 시즌은 결과에 없다 */
    @Query("""
            select p.tradingSeasonId as seasonId, count(p) as participantCount
            from SeasonParticipant p
            where p.tradingSeasonId in :seasonIds
            group by p.tradingSeasonId
            """)
    List<SeasonParticipantCount> countByTradingSeasonIds(@Param("seasonIds") List<Long> seasonIds);

    interface SeasonParticipantCount {
        Long getSeasonId();

        long getParticipantCount();
    }
}
