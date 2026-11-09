package com.wanted.moneybeetle.domain.housing.model.entity;

/* comment.     : 이 클래스가 청약통장
*   담당 : SCM (모의 청약) : 통장이 스스로 만들어지고(create), 오늘 냈는지 답하고(isPaidToday), 납입을 반영(deposit)
*   테이블 subscription_account (가입 시 1인 1개)
*  */

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
@Entity
@Table(name = "subscription_account")
public class SubscriptionAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 번호는 MySQL의 AUTO_INCREMENT가 매기게 맡긴다
    @Column(name = "subscription_account_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "recognized_amount", nullable = false)
    private long recognizedAmount;     // 누적 인정금액

    @Column(name = "paid_count", nullable = false)
    private int paidCount;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;

    // ① 통장 만들기 (회원가입할 때 1번) - 팀 규칙 "생성은 정적 팩토리 create(...)"
    //    처음에는 잔액 0, 인정금액 0, 0회차, 납입한 적 없음(null)
    public static SubscriptionAccount create(Long memberId) {
        SubscriptionAccount account = new SubscriptionAccount();   // protected 생성자는 클래스 안에서는 쓸 수 있다
        account.memberId = memberId;
        account.balance = 0;
        account.recognizedAmount = 0;
        account.paidCount = 0;          // ⓐ 처음 회차
        return account;
    }

    // ② 오늘 이미 납입했나? (하루 1회, DP-35)
    public boolean isPaidToday(LocalDate today) {
        return today.equals(lastPaidDate);         // ⓑ 마지막 납입일 필드
    }

    // ③ 납입하기 : 잔액·인정금액 더하고, 회차 +1, 마지막 납입일 = 오늘
    //    (금액 범위·캐시 부족 검사는 Service 가 먼저 한다)
    public void deposit(long amount, long recognized, LocalDate today) {
        this.balance += amount;
        this.recognizedAmount += recognized;     // ⓒ 이번에 인정된 금액
        this.paidCount ++;               // ⓓ 1 늘리기
        this.lastPaidDate = today;
    }

}
