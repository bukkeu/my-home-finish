package com.wanted.moneybeetle.domain.housing.model.entity;

/* comment.     : 이 클래스가 청약통장
*   담당 : SCM (모의 청약) : 통장이 스스로 만들어지고(create), 오늘 냈는지 답하고(isPaidToday), 납입 규칙을 지켜 납입을 반영(deposit)
*   테이블 subscription_account (가입 시 1인 1개)
*   - 납입 규칙(SP-70, DP-35)은 통장이 지킨다 (#291) : 1회 최소 ~ 최대 납입액, 하루 1회, 인정금액은 하루 한도까지
*     어디서 deposit 을 불러도 규칙이 깨지지 않게, 검사를 Service 가 아니라 이 메서드 안에서 한다
*   - 한도 세 값(DepositLimits)은 운영 설정에서 읽어 Service 가 넘겨준다 (#327, DepositLimitPolicy. 기본 20,000 / 500,000 / 250,000)
*     오류 문구에도 그 값을 넣어 화면 안내와 어긋나지 않게 한다
*  */

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.policy.DepositLimits;
import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
@Entity
@Table(name = "subscription_account")
public class SubscriptionAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 번호는 MySQL의 AUTO_INCREMENT가 매기게 맡긴다
    @Column(name = "subscription_account_id")
    private Long subscriptionAccountId;

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

    // ③ 납입하기 : 규칙을 먼저 확인하고, 잔액·인정금액 더하고, 회차 +1, 마지막 납입일 = 오늘
    //    규칙을 어기면 아무 값도 바꾸지 않고 예외 (금액 범위 → 하루 1회 순서). 캐시 부족은 캐시를 가진 growth 가 확인한다
    //    돌려주는 값 = 이번 납입의 인정금액 (납입 내역에 같이 남긴다)
    //    한도를 넘는 금액도 잔액에는 쌓이고, 인정금액에만 하루 한도까지 반영된다
    public long deposit(long amount, LocalDate today, DepositLimits limits) {
        if (amount < limits.minAmount()) {
            throw new BusinessException(HousingErrorCode.DEPOSIT_BELOW_MIN,
                    String.format(Locale.KOREA, "최소 %,d 캐시부터 납입할 수 있어요", limits.minAmount()));
        }
        if (amount > limits.maxAmount()) {
            throw new BusinessException(HousingErrorCode.DEPOSIT_ABOVE_MAX,
                    String.format(Locale.KOREA, "한 번에 최대 %,d 캐시까지 납입할 수 있어요", limits.maxAmount()));
        }
        if (isPaidToday(today)) {
            throw new BusinessException(HousingErrorCode.ALREADY_PAID_TODAY);
        }
        long recognized = limits.recognizedAmountOf(amount);
        this.balance += amount;
        this.recognizedAmount += recognized;     // ⓒ 이번에 인정된 금액
        this.paidCount ++;               // ⓓ 1 늘리기
        this.lastPaidDate = today;
        return recognized;
    }

}
