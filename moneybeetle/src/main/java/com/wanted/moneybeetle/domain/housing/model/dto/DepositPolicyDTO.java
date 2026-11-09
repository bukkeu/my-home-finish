package com.wanted.moneybeetle.domain.housing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 납입 기준 (SP-70 확정, #76) - API 65 응답의 depositPolicy
*   - 1회 20,000 ~ 500,000 캐시, 하루 1회
*   - 납입액은 모두 잔액에 들어가고, 인정금액은 하루 250,000 까지만 쌓인다
*   TODO 운영 값은 system_setting 에서 읽도록 바꾼다 (설정 조회 Service 가 생기면, 해금 레벨과 같은 방식)
*  */
@Getter
@AllArgsConstructor
public class DepositPolicyDTO {

    public static final long MIN_AMOUNT = 20_000;
    public static final long MAX_AMOUNT = 500_000;
    public static final long DAILY_RECOGNIZED_LIMIT = 250_000;

    private long minAmount;
    private long maxAmount;
    private long dailyRecognizedLimit;

    // 지금 적용 중인 기준
    public static DepositPolicyDTO current() {
        return new DepositPolicyDTO(MIN_AMOUNT, MAX_AMOUNT, DAILY_RECOGNIZED_LIMIT);
    }

    // 이번 납입의 인정금액 = 납입액과 하루 한도 중 작은 값 (예: 300,000 -> 250,000)
    public static long recognizedAmountOf(long amount) {
        return Math.min(amount, DAILY_RECOGNIZED_LIMIT);
    }
}
