package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trading_season
*  */
public interface TradingSeasonRepository extends JpaRepository<TradingSeason, Long> {

    /** 사용자에게 보이는 시즌 : 주어진 상태(진행 중·휴장 중·정산 중·종료) 중 시작일이 가장 최근인 것 (#24) */
    Optional<TradingSeason> findFirstByStatusInOrderByStartDateDesc(List<String> statuses);
}
