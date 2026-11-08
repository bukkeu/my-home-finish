package com.wanted.moneybeetle.global.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* comment.
*   담당 : SCM (회원·공통)
*   레벨로 해금되는 기능 (SP-24, INV-01, SUB-01)
*   - settingKey   : system_setting 테이블에서 해금 레벨을 읽을 때 쓰는 키 (관리자가 바꿀 수 있는 운영 값)
*   - defaultLevel : 설정 값을 읽기 전까지 쓰는 기본 해금 레벨 (sample-data.sql 의 값과 같음)
*  */
@Getter
@RequiredArgsConstructor
public enum UnlockFeature {

    TRADING("UNLOCK_LEVEL_TRADING", 8),   // 모의투자
    HOUSING("UNLOCK_LEVEL_HOUSING", 12);  // 모의 청약

    private final String settingKey;
    private final int defaultLevel;
}
