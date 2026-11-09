package com.wanted.moneybeetle.domain.growth.model.dao;

import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   테이블 reward_rule
*  */
public interface RewardRuleRepository extends JpaRepository<RewardRule, Long> {

    /** 사용 중인 보상 규칙을 코드로 찾는다 (꺼진 규칙은 지급하지 않는다) */
    Optional<RewardRule> findByCodeAndActiveTrue(String code);

    /** 관리자 화면용: 꺼진 규칙까지 모두, 번호 순서로 (API 108) */
    List<RewardRule> findAllByOrderByRewardRuleIdAsc();
}
