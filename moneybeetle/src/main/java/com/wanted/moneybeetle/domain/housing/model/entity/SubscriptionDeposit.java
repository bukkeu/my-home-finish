package com.wanted.moneybeetle.domain.housing.model.entity;

import com.wanted.moneybeetle.global.common.CreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)    : 통장 납입 내역
*   테이블 subscription_deposit (하루 1회)
*  */

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
@Entity
@Table(name = "subscription_deposit")
public class SubscriptionDeposit extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_deposit_id")
    private Long id;

    @Column(name = "subscription_account_id", nullable = false)
    private Long subscriptionAccountId;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "recognized_amount", nullable = false)
    private int recognizedAmount;            // 이번 납입의 인정금액 (최대 250,000)

    @Column(name = "paid_count_after", nullable = false)
    private int paidCountAfter;


    // 납입 내역 만들기 (납입할 때마다 1줄)
    public static SubscriptionDeposit create(Long subscriptionAccountId, LocalDate paidDate,
                                             int amount, int recognizedAmount, int paidCountAfter) {
        SubscriptionDeposit deposit = new SubscriptionDeposit();
        deposit.subscriptionAccountId = subscriptionAccountId;
        deposit.paidDate = paidDate;
        deposit.amount = amount;
        deposit.recognizedAmount = recognizedAmount;
        deposit.paidCountAfter = paidCountAfter;
        return deposit;
    }

}
