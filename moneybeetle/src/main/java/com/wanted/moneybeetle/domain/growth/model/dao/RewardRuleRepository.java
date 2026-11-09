package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 reward_rule
*  */
public interface RewardRuleRepository extends JpaRepository<RewardRule, Long> {

    /** 사용 중인 보상 규칙을 코드로 찾는다 (꺼진 규칙은 지급하지 않는다) */
    Optional<RewardRule> findByCodeAndActiveTrue(String code);

    /**
     * 관리자가 규칙을 수정하려고 행을 잠그고 읽는다 (비관적 락, 트랜잭션이 끝날 때까지 유지).
     * 관리자 둘이 같은 규칙을 동시에 고쳐도 차례로 처리되어, 작업 기록의 변경 전·후 값이 실제 수정 순서와 이어진다 (ADM-29).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RewardRule r where r.rewardRuleId = :ruleId")
    Optional<RewardRule> findByIdForUpdate(@Param("ruleId") Long ruleId);

    /** 관리자 화면용: 꺼진 규칙까지 모두, 번호 순서로 (API 108) */
    List<RewardRule> findAllByOrderByRewardRuleIdAsc();
}
