package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 납입 요청 (API 66) - { "amount": 300000 }
*   금액 범위(20,000~500,000)는 정해진 에러 코드(DEPOSIT_BELOW_MIN / ABOVE_MAX)로 답해야 해서 Service 에서 검사한다
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequestDTO {

    private long amount;
}
