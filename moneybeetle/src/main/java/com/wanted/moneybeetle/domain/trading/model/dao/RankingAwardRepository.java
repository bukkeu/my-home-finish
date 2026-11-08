package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.RankingAward;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 ranking_award
*  */
public interface RankingAwardRepository extends JpaRepository<RankingAward, Long> {

}
