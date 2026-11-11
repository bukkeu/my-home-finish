package com.wanted.moneybeetle.domain.housing.model.policy;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 납입 한도 세 값 (SP-70, #327). 운영 설정에서 읽은 값을 한 번에 들고 다닌다
*   - minAmount : 1회 최소 납입액, maxAmount : 1회 최대 납입액, dailyRecognizedLimit : 하루 인정금액 한도
*   - DEFAULT : 설정이 없거나 잘못됐을 때 쓰는 확정값 (SP-70, #76)
*   - 통장(SubscriptionAccount.deposit)의 규칙 검사, 화면 안내(DepositPolicyDTO), 오류 문구가 모두 이 값 하나를 쓴다
*  */
public record DepositLimits(long minAmount, long maxAmount, long dailyRecognizedLimit) {

    public static final DepositLimits DEFAULT = new DepositLimits(20_000, 500_000, 250_000);

    /** 세 값이 모두 1 이상이고 최소 ≤ 최대인가 (아니면 납입이 막히거나 규칙이 뒤집힌다) */
    public boolean isValid() {
        return minAmount >= 1 && maxAmount >= minAmount && dailyRecognizedLimit >= 1;
    }

    /** 이번 납입의 인정금액 = 납입액과 하루 한도 중 작은 값 (예: 300,000 -> 250,000) */
    public long recognizedAmountOf(long amount) {
        return Math.min(amount, dailyRecognizedLimit);
    }
}
