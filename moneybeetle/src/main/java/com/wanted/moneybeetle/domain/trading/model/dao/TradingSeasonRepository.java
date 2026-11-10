package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : PM (모의투자)
*   테이블 trading_season
*  */
public interface TradingSeasonRepository extends JpaRepository<TradingSeason, Long> {

    /** 사용자에게 보이는 시즌 : 주어진 상태(진행 중·휴장 중·정산 중·종료) 중 시작일이 가장 최근인 것 (#24) */
    Optional<TradingSeason> findFirstByStatusInOrderByStartDateDesc(List<String> statuses);

    /** 주문할 때 보는 시즌을 공유 잠금으로 읽어 턴 진행과 주문 검증을 같은 시즌 행에서 동기화한다 (#25) */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<TradingSeason> findTopByStatusInOrderByStartDateDesc(List<String> statuses);

    /** 시장 변경 전에 준비·진행·휴장 시즌을 쓰기 잠금으로 읽어 시즌 상태 변경 및 턴 진행과 동기화한다 (#44) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TradingSeason s where s.status in :statuses order by s.startDate desc")
    List<TradingSeason> findAllByStatusInForUpdate(@Param("statuses") List<String> statuses);

    /** 관리자 시즌 목록 : 최근 시작한 시즌부터 */
    List<TradingSeason> findAllByOrderByStartDateDesc();

    /** 턴을 넘길 때 같은 시즌을 두 관리자가 동시에 넘기지 못하게 행을 잠그고 읽는다 (#44) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TradingSeason s where s.tradingSeasonId = :seasonId")
    Optional<TradingSeason> findByIdForUpdate(@Param("seasonId") Long seasonId);
}
