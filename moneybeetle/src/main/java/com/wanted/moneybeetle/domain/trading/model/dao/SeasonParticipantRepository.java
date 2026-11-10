package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : PM (모의투자)
*   테이블 season_participant
*  */
public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {

    /** 회원의 그 시즌 참가 기록 (#24 내 보유) */
    Optional<SeasonParticipant> findByTradingSeasonIdAndMemberId(Long tradingSeasonId, Long memberId);

    /** 이미 참가했는지 (#23) */
    boolean existsByTradingSeasonIdAndMemberId(Long tradingSeasonId, Long memberId);

    /**
     * 이 시즌보다 먼저 시작한 시즌 중 가장 최근 참가 기록 : 이월금(carryover_out)을 가져온다 (#23, SP-63).
     * 참가한 순서(joinedAt)가 아니라 시즌 시작일로 고른다 - 기간이 겹치는 시즌에서 나중 시즌에 먼저 참가해도
     * 앞선 시즌이 나중 시즌의 이월금을 받지 않는다. 시작일이 같으면 시즌 번호가 작은 쪽을 앞선 시즌으로 본다
     */
    @Query("""
            select p
            from SeasonParticipant p, TradingSeason s, TradingSeason current
            where p.tradingSeasonId = s.tradingSeasonId
              and current.tradingSeasonId = :seasonId
              and p.memberId = :memberId
              and (s.startDate < current.startDate
                   or (s.startDate = current.startDate and s.tradingSeasonId < current.tradingSeasonId))
            order by s.startDate desc, s.tradingSeasonId desc
            """)
    List<SeasonParticipant> findEarlierParticipations(@Param("memberId") Long memberId,
            @Param("seasonId") Long seasonId, Pageable pageable);

    default Optional<SeasonParticipant> findPreviousParticipation(Long memberId, Long seasonId) {
        return findEarlierParticipations(memberId, seasonId, PageRequest.of(0, 1)).stream().findFirst();
    }

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

    /** 주문할 때 같은 참가자의 현금·보유가 동시에 바뀌지 않도록 행을 잠그고 읽는다 (#25) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SeasonParticipant p where p.tradingSeasonId = :seasonId and p.memberId = :memberId")
    Optional<SeasonParticipant> findForUpdateBySeasonAndMember(@Param("seasonId") Long tradingSeasonId,
            @Param("memberId") Long memberId);

    /** 정산으로 순자산 순위가 확정된 참가자 (시즌 랭킹 조회 #26) */
    Page<SeasonParticipant> findByTradingSeasonIdAndAssetRankIsNotNull(Long tradingSeasonId, Pageable pageable);

    /** 정산으로 수익률 순위가 확정된 참가자 (시즌 랭킹 조회 #26) */
    Page<SeasonParticipant> findByTradingSeasonIdAndReturnRankIsNotNull(Long tradingSeasonId, Pageable pageable);

    /** 시즌의 모든 참가자 (관리자 참가자 목록·랭킹 미리보기 #45, 정산 #46) */
    List<SeasonParticipant> findByTradingSeasonIdOrderBySeasonParticipantIdAsc(Long tradingSeasonId);
}
