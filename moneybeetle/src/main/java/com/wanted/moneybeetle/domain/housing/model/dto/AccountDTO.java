package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)    : 내 통장
*   내 청약통장 (API 65) - 잔액, 누적 인정금액, 납입회차, 오늘 납입 여부, 납입 기준, 내 캐시
*  */
@Getter
@AllArgsConstructor
public class AccountDTO {

    private Long accountId;
    private long balance;
    private long recognizedAmount;
    private int paidCount;
    private LocalDate lastPaidDate;       // 한 번도 안 냈으면 null
    private boolean paidToday;
    private DepositPolicyDTO depositPolicy;
    private long cash;                    // 내 캐시 잔액 (RewardService.getBalance)

    // 오늘 납입 여부는 오늘 날짜가, 캐시는 다른 도메인(growth)이 알려 준다
    public static AccountDTO from(SubscriptionAccount account, LocalDate today, long cash) {
        return new AccountDTO(
                account.getSubscriptionAccountId(),
                account.getBalance(),
                account.getRecognizedAmount(),
                account.getPaidCount(),
                account.getLastPaidDate(),
                account.isPaidToday(today),
                DepositPolicyDTO.current(),
                cash
        );
    }
}
