package com.wanted.moneybeetle.domain.housing.model.dao;

import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/* comment.
*   담당 : SCM (모의 청약)    : 통장 찾기
*   테이블 subscription_account
*  */
@Repository
public interface SubscriptionAccountRepository extends JpaRepository<SubscriptionAccount, Long> {   // ⓐ 통장 엔티티

    // 회원 번호로 내 통장 찾기 (1인 1개라서 하나만 나옴)
    Optional<SubscriptionAccount> findByMemberId(Long memberId);   // ⓑ 필드 이름

    // 납입할 때 : 같은 통장에 동시에 두 요청이 와도 하나씩 처리되도록 잠금을 걸고 꺼낸다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SubscriptionAccount> findWithLockByMemberId(Long memberId);
}
