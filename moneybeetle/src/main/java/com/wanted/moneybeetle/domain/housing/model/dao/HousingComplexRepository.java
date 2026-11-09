package com.wanted.moneybeetle.domain.housing.model.dao;

/* comment.
*   담당 : SCM (모의 청약)
*   테이블 housing_complex
*  */

import com.wanted.moneybeetle.domain.housing.model.entity.HousingComplex;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
