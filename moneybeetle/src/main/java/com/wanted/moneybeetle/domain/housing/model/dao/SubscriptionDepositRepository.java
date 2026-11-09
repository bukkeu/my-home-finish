package com.wanted.moneybeetle.domain.housing.model.dao;

import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionDeposit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/* comment.
*   담당 : SCM (모의 청약)    : 통장 내역
*   테이블 subscription_deposit
*  */
@Repository
public interface SubscriptionDepositRepository extends JpaRepository<SubscriptionDeposit, Long> {   // ⓒ 납입 내역 엔티티

    // 내 통장의 납입 내역, 페이지 단위로 (최신순 정렬은 Service 에서 Pageable 로 정한다)
    Page<SubscriptionDeposit> findBySubscriptionAccountId(Long subscriptionAccountId, Pageable pageable);
}