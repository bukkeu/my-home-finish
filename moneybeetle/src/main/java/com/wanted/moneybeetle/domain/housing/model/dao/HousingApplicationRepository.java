package com.wanted.moneybeetle.domain.housing.model.dao;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/* comment.
*   담당 : SCM (모의 청약)
*   - 이 단지 신청자는 몇 명?
*   - 이 회원이 이 단지에 신청한 내역은?
*   - 이 회원이 신청한 단지들은?(여러 단지를 한 번에)
*   테이블 housing_application
*  */
@Repository
public interface HousingApplicationRepository extends JpaRepository<HousingApplication, Long> {

    long countByHousingComplexId(Long housingComplexId);

    // ② 회원 + 단지로 내 신청 찾기
    Optional<HousingApplication> findByMemberIdAndHousingComplexId(Long memberId, Long housingComplexId);   // ⓒ

    // ③ 회원이 여러 단지 중 어디에 신청했는지 한 번에 찾기 (캘린더 목록용)
    //    In = "이 목록 안에 있는 것" → SQL 의 WHERE housing_complex_id IN (1, 2, 3)
    List<HousingApplication> findByMemberIdAndHousingComplexIdIn(Long memberId, List<Long> housingComplexIds);
}
