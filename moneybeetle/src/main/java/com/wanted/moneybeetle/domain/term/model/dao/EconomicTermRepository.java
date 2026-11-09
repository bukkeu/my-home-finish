package com.wanted.moneybeetle.domain.term.model.dao;

import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (경제 용어)
*   테이블 economic_term
*  */
public interface EconomicTermRepository extends JpaRepository<EconomicTerm, Long> {

    boolean existsByTerm(String term);

    boolean existsByTermAndEconomicTermIdNot(String term, Long economicTermId);

    /** 사용 중인 용어를 등록 순서(번호 순)로. 오늘의 용어 자동 선정에 쓴다 (SP-46) */
    List<EconomicTerm> findByActiveTrueOrderByEconomicTermIdAsc();

    /** 번호로 고른 용어 중 사용 중인 것만 (날짜 지정 때 존재·사용 여부 확인) */
    List<EconomicTerm> findByEconomicTermIdInAndActiveTrue(Collection<Long> ids);

    /** 관리자 목록: 용어·뜻에 keyword 가 들어간 것을 등록 순으로. keyword 가 null 이면 전체 (API 95) */
    @Query("select t from EconomicTerm t "
            + "where (:keyword is null or t.term like concat('%', :keyword, '%') "
            + "or t.description like concat('%', :keyword, '%')) "
            + "order by t.economicTermId asc")
    Slice<EconomicTerm> search(@Param("keyword") String keyword, Pageable pageable);
}
