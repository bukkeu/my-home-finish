package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (성장)
*   테이블 exp_history
*  */
public interface ExpHistoryRepository extends JpaRepository<ExpHistory, Long> {

    /** 같은 강의로 이미 경험치를 받았는가 (같은 강의 최초 1회만, SP-22) */
    boolean existsByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);

    /** 경험치 내역 최신순 (API 23) */
    Page<ExpHistory> findByMemberIdOrderByCreatedAtDescExpHistoryIdDesc(Long memberId, Pageable pageable);
}
