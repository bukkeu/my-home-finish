package com.wanted.moneybeetle.domain.housing.model.dao;

import com.wanted.moneybeetle.domain.housing.model.entity.HousingApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // #47 관리자 단지 목록 : 여러 단지의 신청자 수를 한 번에 센다 (단지마다 count 를 부르면 N+1)
    //  결과 한 줄 = [단지 ID, 신청자 수]
    @Query("SELECT a.housingComplexId, COUNT(a) FROM HousingApplication a "
            + "WHERE a.housingComplexId IN :complexIds GROUP BY a.housingComplexId")
    List<Object[]> countGroupByHousingComplexIdIn(@Param("complexIds") List<Long> complexIds);
}
