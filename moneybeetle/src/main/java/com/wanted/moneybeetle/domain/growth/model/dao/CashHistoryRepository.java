package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (성장)
*   테이블 cash_history
*  */
public interface CashHistoryRepository extends JpaRepository<CashHistory, Long> {

    /** 같은 원인으로 이미 기록했는가 (이중 지급 방지: DB UNIQUE 와 함께 쓴다) */
    boolean existsByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);

    /** 캐시 내역 최신순 (API 22) */
    Page<CashHistory> findByMemberIdOrderByCreatedAtDescCashHistoryIdDesc(Long memberId, Pageable pageable);

    /** 캐시 내역을 종류로 거른다. 적립: EARN·ADMIN_GIVE, 사용: USE·ADMIN_TAKE */
    Page<CashHistory> findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
            Long memberId, Collection<String> historyTypes, Pageable pageable);
}
