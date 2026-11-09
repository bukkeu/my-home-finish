package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 exp_history
*  */
public interface ExpHistoryRepository extends JpaRepository<ExpHistory, Long> {

    /** 같은 강의로 이미 경험치를 받았는가 (같은 강의 최초 1회만, SP-22). 잠금 없는 일반 조회다 */
    boolean existsByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);

    /**
     * 같은 강의의 경험치 기록을 잠금 조회(SELECT ... FOR UPDATE)로 찾는다. RewardService 의 이중 지급 확인용이다.
     * 일반 조회는 REPEATABLE READ 스냅샷을 보지만 잠금 조회는 최신 커밋 값을 읽는다 (CashHistoryRepository 설명 참고).
     * (member_id, source_type, source_id)는 UNIQUE 라서 결과는 0 또는 1건이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExpHistory> findWithLockByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);

    /** 경험치 내역 최신순 (API 23) */
    Page<ExpHistory> findByMemberIdOrderByCreatedAtDescExpHistoryIdDesc(Long memberId, Pageable pageable);
}
