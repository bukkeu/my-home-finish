package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 cash_history
*  */
public interface CashHistoryRepository extends JpaRepository<CashHistory, Long> {

    /** 같은 원인으로 이미 기록했는가 (이중 지급 방지: DB UNIQUE 와 함께 쓴다). 잠금 없는 일반 조회다 */
    boolean existsByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);

    /**
     * 같은 원인의 기록을 잠금 조회(SELECT ... FOR UPDATE)로 찾는다. RewardService 의 이중 지급·이중 차감 확인용이다.
     * MySQL 기본 격리(REPEATABLE READ)에서 일반 조회는 트랜잭션의 첫 조회 시점 스냅샷을 보기 때문에, 바깥 트랜잭션
     * (예: 퀴즈 제출)이 이미 스냅샷을 만든 뒤 호출되면 그 사이에 다른 요청이 커밋한 기록을 못 본다.
     * 잠금 조회는 항상 최신 커밋 값을 읽는다. (member_id, source_type, source_id)는 UNIQUE 라서 결과는 0 또는 1건이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CashHistory> findWithLockByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);

    /** 캐시 내역 최신순 (API 22) */
    Page<CashHistory> findByMemberIdOrderByCreatedAtDescCashHistoryIdDesc(Long memberId, Pageable pageable);

    /** 캐시 내역을 종류로 거른다. 적립: EARN·ADMIN_GIVE, 사용: USE·ADMIN_TAKE */
    Page<CashHistory> findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
            Long memberId, Collection<String> historyTypes, Pageable pageable);
}
