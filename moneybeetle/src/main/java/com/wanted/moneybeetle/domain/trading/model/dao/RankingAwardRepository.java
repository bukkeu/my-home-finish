package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.RankingAward;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 ranking_award
*  */
public interface RankingAwardRepository extends JpaRepository<RankingAward, Long> {

    /** 그 시즌·랭킹 종류에서 주어진 회원들의 수상 기록 (시즌 랭킹 조회 #26) */
    List<RankingAward> findByTradingSeasonIdAndRankingTypeAndMemberIdIn(Long tradingSeasonId, String rankingType,
            Collection<Long> memberIds);

    /** 이미 준 수상인지 (보상 지급을 여러 번 눌러도 한 번만 #46) */
    boolean existsByTradingSeasonIdAndMemberIdAndRankingType(Long tradingSeasonId, Long memberId, String rankingType);
}
