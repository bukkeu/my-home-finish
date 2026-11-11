package com.wanted.moneybeetle.domain.housing.model.dto;

import com.wanted.moneybeetle.domain.housing.model.policy.DepositLimits;
import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 납입 기준 (SP-70 확정, #76) - API 65 응답의 depositPolicy. 화면에 보여 줄 값만 담는다
*   - 1회 20,000 ~ 500,000 캐시, 하루 1회
*   - 납입액은 모두 잔액에 들어가고, 인정금액은 하루 250,000 까지만 쌓인다
*   - 규칙은 통장(SubscriptionAccount)이 지키고(#291), 한도 값은 운영 설정에서 읽는다(DepositLimitPolicy, #327). 이 DTO 는 그 값을 화면에 옮길 뿐이다
*  */
@Getter
@AllArgsConstructor
public class DepositPolicyDTO {

    private long minAmount;
    private long maxAmount;
    private long dailyRecognizedLimit;

    // 지금 적용 중인 기준 (운영 설정에서 읽은 한도)
    public static DepositPolicyDTO from(DepositLimits limits) {
        return new DepositPolicyDTO(limits.minAmount(), limits.maxAmount(), limits.dailyRecognizedLimit());
    }
}
