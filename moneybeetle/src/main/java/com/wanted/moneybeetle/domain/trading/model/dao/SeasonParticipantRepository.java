package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.SeasonParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 season_participant
*  */
public interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, Long> {

}
