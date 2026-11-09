package com.wanted.moneybeetle.domain.housing.model.dao;

/* comment.
*   담당 : SCM (모의 청약)
*   테이블 housing_complex
*  */

import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HousingComplexRepository extends JpaRepository<HousingComplex, Long> {

    // ② 청약 캘린더 (API 63) : 그 달에 일정이 걸쳐 있는 단지, 삭제 안 된 것만, 신청 시작일 순서
    //    "공고일 <= 그 달 마지막 날" 그리고 "발표일 >= 그 달 첫날"
    List<HousingComplex> findByNoticeDateLessThanEqualAndAnnounceDateGreaterThanEqualAndDeletedAtIsNullOrderByApplyStartDate(
            LocalDate monthEnd, LocalDate monthStart);

    Optional<HousingComplex> findByIdAndDeletedAtIsNull(Long id);

    // #47 관리자 단지 목록 (API 142) : 삭제 안 된 단지, 신청 시작일 최신순
    List<HousingComplex> findByDeletedAtIsNullOrderByApplyStartDateDesc();

    // #48 단지 행 잠금 조회 : SELECT ... FOR UPDATE
    //  신청(#29)·관리자 수정·삭제(#47)·산정·발표(#48)가 모두 이 메서드로 단지를 먼저 잠근다.
    //  같은 단지에 대한 작업이 한 번에 하나씩만 진행돼 "신청자 없음 확인 → 변경" 사이에 신청이 끼어들지 못한다 (SP-76)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM HousingComplex c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<HousingComplex> findForUpdate(@Param("id") Long id);
}
