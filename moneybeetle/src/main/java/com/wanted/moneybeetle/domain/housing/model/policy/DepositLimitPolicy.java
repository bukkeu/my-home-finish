package com.wanted.moneybeetle.domain.housing.model.policy;

import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 납입 한도를 운영 설정에서 읽는다 (#327, SP-70, NFR-13)
*   - system_setting : DEPOSIT_MIN_AMOUNT(20000), DEPOSIT_MAX_AMOUNT(500000), DEPOSIT_DAILY_RECOGNIZED_LIMIT(250000)
*     관리자 운영 설정(API 112)으로 바꾸면 다음 요청부터 납입 검사·화면 안내·오류 문구에 바로 반영된다 (매번 읽는다)
*   - 설정은 운영 도메인 Service 메서드(SystemSettingService.findIntValue)로만 읽는다 (역할분담 §7). 해금 레벨의 UnlockLevelProvider 와 같은 방식
*   - 값이 없거나 숫자가 아니면 그 값만 기본값. 셋을 합쳐 1 미만이거나 최소 > 최대처럼 규칙이 뒤집히면 세 값 모두 기본값
*     (설정 실수로 납입이 모두 막히거나 한도가 사라지지 않게)
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositLimitPolicy {

    static final String KEY_MIN = "DEPOSIT_MIN_AMOUNT";
    static final String KEY_MAX = "DEPOSIT_MAX_AMOUNT";
    static final String KEY_DAILY_RECOGNIZED = "DEPOSIT_DAILY_RECOGNIZED_LIMIT";

    private final SystemSettingService systemSettingService;

    public DepositLimits getLimits() {
        DepositLimits def = DepositLimits.DEFAULT;
        DepositLimits limits = new DepositLimits(
                systemSettingService.findIntValue(KEY_MIN).orElse((int) def.minAmount()),
                systemSettingService.findIntValue(KEY_MAX).orElse((int) def.maxAmount()),
                systemSettingService.findIntValue(KEY_DAILY_RECOGNIZED).orElse((int) def.dailyRecognizedLimit()));
        if (!limits.isValid()) {
            log.warn("청약통장 납입 한도 설정이 올바르지 않아 기본값을 씁니다: {}", limits);
            return def;
        }
        return limits;
    }
}
