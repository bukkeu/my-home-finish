package com.wanted.moneybeetle.domain.term.model.dao;

import com.wanted.moneybeetle.domain.term.model.entity.TermSchedule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (경제 용어)
*   테이블 term_schedule
*  */
public interface TermScheduleRepository extends JpaRepository<TermSchedule, Long> {

    List<TermSchedule> findByShowDateOrderBySortOrderAsc(LocalDate showDate);

    /** 기간(양 끝 포함)의 일정을 날짜·순서 순으로 (월별 지정 현황, API 99) */
    List<TermSchedule> findByShowDateBetweenOrderByShowDateAscSortOrderAsc(LocalDate from, LocalDate to);

    /** 노출 이력이 있는가 (있으면 용어를 지우지 않고 비활성, API 98) */
    boolean existsByEconomicTermId(Long economicTermId);

    /** 그 날짜의 일정을 모두 지운다. 같은 트랜잭션에서 바로 새 일정을 넣으므로 먼저 DB 에 반영한다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TermSchedule s where s.showDate = :showDate")
    int deleteByShowDate(@Param("showDate") LocalDate showDate);

    /** 그 날짜에서 관리자가 직접 지정한 일정만 지운다. 이미 저장된 자동 선정은 처음 정한 대로 유지한다 (지정 해제, API 100-1) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TermSchedule s where s.showDate = :showDate and s.auto = false")
    int deleteManualByShowDate(@Param("showDate") LocalDate showDate);
}
