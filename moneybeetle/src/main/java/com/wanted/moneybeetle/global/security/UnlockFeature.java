package com.wanted.moneybeetle.global.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   레벨로 해금되는 기능 (SP-24, INV-01, SUB-01)
*   - settingKey   : system_setting 테이블에서 해금 레벨을 읽을 때 쓰는 키 (관리자가 바꿀 수 있는 운영 값)
*   - defaultLevel : 설정이 없거나 잘못됐을 때 쓰는 기본 해금 레벨 (sample-data.sql 의 값과 같음)
*   - fromSetting  : 해금 레벨을 운영 설정에서 읽는지 (#223). 실제 값은 UnlockLevelProvider 가 정한다
*       모의 청약 : 읽는다
*       모의투자  : 아직 기본값. 시즌마다 해금 레벨(trading_season.unlock_level)이 따로 있어 어느 쪽을 기준으로 할지
*                  모의투자 담당과 정한 뒤 바꾼다 (#223)
*  */
@Getter
@RequiredArgsConstructor
public enum UnlockFeature {

    TRADING("UNLOCK_LEVEL_TRADING", 8, false),   // 모의투자
    HOUSING("UNLOCK_LEVEL_HOUSING", 12, true);   // 모의 청약

    private final String settingKey;
    private final int defaultLevel;
    private final boolean fromSetting;
}
