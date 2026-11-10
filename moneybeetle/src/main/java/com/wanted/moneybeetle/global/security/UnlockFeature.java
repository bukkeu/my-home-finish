package com.wanted.moneybeetle.global.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   레벨로 해금되는 기능 (SP-24, INV-01, SUB-01)
*   - settingKey   : system_setting 테이블에서 해금 레벨을 읽을 때 쓰는 키 (관리자가 바꿀 수 있는 운영 값)
*   - defaultLevel : 설정이 없거나 잘못됐을 때 쓰는 기본 해금 레벨 (sample-data.sql 의 값과 같음)
*   실제로 적용하는 값은 UnlockLevelProvider 가 운영 설정에서 읽어 정한다 (#223)
*   모의투자 해금은 시즌과 상관없이 이 값 하나로 정한다. 시즌은 랭킹 경쟁 단위다 (팀 결정 2026-11-10)
*  */
@Getter
@RequiredArgsConstructor
public enum UnlockFeature {

    TRADING("UNLOCK_LEVEL_TRADING", 8),   // 모의투자
    HOUSING("UNLOCK_LEVEL_HOUSING", 12);  // 모의 청약

    private final String settingKey;
    private final int defaultLevel;
}
