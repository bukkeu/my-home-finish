package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM 이재성 (모의투자)
*   테이블 trading_season
*  */
public interface TradingSeasonRepository extends JpaRepository<TradingSeason, Long> {

    /** 관리자 시즌 목록 : 최근 시작한 시즌부터 */
    List<TradingSeason> findAllByOrderByStartDateDesc();
}
