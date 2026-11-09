package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 season_turn
*  */
public interface SeasonTurnRepository extends JpaRepository<SeasonTurn, Long> {

}
