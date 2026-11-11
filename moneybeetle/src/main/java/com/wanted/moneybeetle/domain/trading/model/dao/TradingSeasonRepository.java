package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    /** 시장 변경 전에 준비·진행·휴장 시즌을 쓰기 잠금으로 읽어 시즌 상태 변경 및 턴 진행과 동기화한다 (#44) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TradingSeason s where s.status in :statuses order by s.startDate desc")
    List<TradingSeason> findAllByStatusInForUpdate(@Param("statuses") List<String> statuses);

    /** 관리자 시즌 목록 : 최근 시작한 시즌부터 */
    List<TradingSeason> findAllByOrderByStartDateDesc();

    /** 보이는 시즌의 번호만 읽는다. 시즌 객체를 영속성 컨텍스트에 올리지 않아, 잠금 읽기로 최신 상태를 다시 읽을 수 있다 (#25) */
    @Query("select s.tradingSeasonId from TradingSeason s where s.status in :statuses order by s.startDate desc")
    List<Long> findSeasonIdsByStatusIn(@Param("statuses") List<String> statuses, Pageable pageable);

    /**
     * 주문이 시즌 행을 공유 잠금(FOR SHARE)으로 읽는다 (#25, #208). 주문끼리는 서로 기다리지 않고,
     * 턴을 넘기는 쪽(findByIdForUpdate)은 진행 중인 주문이 끝나기를, 주문은 진행 중인 턴 넘김이 끝나기를 기다린다
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from TradingSeason s where s.tradingSeasonId = :seasonId")
    Optional<TradingSeason> findByIdForShare(@Param("seasonId") Long seasonId);

    /** 턴을 넘길 때 같은 시즌을 두 관리자가 동시에 넘기지 못하게 행을 잠그고 읽는다 (#44) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TradingSeason s where s.tradingSeasonId = :seasonId")
    Optional<TradingSeason> findByIdForUpdate(@Param("seasonId") Long seasonId);

    /** 이 시작일보다 먼저 시작한 시즌 중 가장 최근 시즌 = "이전 시즌" (#207) */
    Optional<TradingSeason> findFirstByStartDateBeforeOrderByStartDateDesc(LocalDate startDate);

    /** 기간(시작일~종료일)이 주어진 기간과 하루라도 겹치는 시즌이 있는가 (#207) */
    boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate periodEnd, LocalDate periodStart);
}
