package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionDeposit;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/* comment.
 *   담당 : SCM (모의 청약)
 *   납입 내역 한 줄 (API 67) API로 내보냄
 *  */
@Getter
@AllArgsConstructor
public class DepositDTO {

    private Long depositId;
    private LocalDate paidDate;
    private int amount;
    private int recognizedAmount;
    private int paidCountAfter;

    public static DepositDTO from(SubscriptionDeposit deposit) {
        return new DepositDTO(
                deposit.getSubscriptionDepositId(),
                deposit.getPaidDate(),
                deposit.getAmount(),
                deposit.getRecognizedAmount(),
                deposit.getPaidCountAfter());
    }
}