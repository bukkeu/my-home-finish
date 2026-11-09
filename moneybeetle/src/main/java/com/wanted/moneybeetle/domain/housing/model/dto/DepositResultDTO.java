package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionDeposit;
import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 납입 결과 (API 66) - 이번 납입 + 납입 후 통장 상태 + 남은 캐시
*  */
@Getter
@AllArgsConstructor
public class DepositResultDTO {

    private Long depositId;
    private long amount;                  // 이번 납입액
    private long recognizedAmount;        // 이번 인정금액
    private long balance;                 // 납입 후 통장 잔액
    private long totalRecognizedAmount;   // 납입 후 누적 인정금액
    private int paidCount;                // 납입 후 회차
    private long cash;                    // 납입 후 내 캐시

    public static DepositResultDTO from(SubscriptionDeposit deposit, SubscriptionAccount account, long cash) {
        return new DepositResultDTO(
                deposit.getId(),
                deposit.getAmount(),
                deposit.getRecognizedAmount(),
                account.getBalance(),
                account.getRecognizedAmount(),
                account.getPaidCount(),
                cash
        );
    }
}
