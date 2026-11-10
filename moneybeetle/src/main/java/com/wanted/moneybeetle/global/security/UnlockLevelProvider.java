package com.wanted.moneybeetle.global.security;

import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   기능별 해금 레벨을 한 곳에서 정한다 (#223, SP-24, NFR-13)
*   - 레벨 검사(LevelCheckInterceptor)와 화면(모의 청약 HousingPageService)이 같은 값을 쓰도록 둘 다 여기서 읽는다
*   - 운영 설정(system_setting, 관리자 API 112 로 바꿈)을 매번 읽는다 → 바꾸면 바로 반영된다
*     모의투자 UNLOCK_LEVEL_TRADING(기본 8), 모의 청약 UNLOCK_LEVEL_HOUSING(기본 12)
*     설정은 운영 도메인 Service 메서드(SystemSettingService.findIntValue)로만 읽는다 (역할분담 §7)
*   - 설정이 없거나, 정수가 아니거나, 1 보다 작으면 기본값 (설정 실수로 기능이 모두에게 열리거나 멈추지 않게)
*  */
@Component
@RequiredArgsConstructor
public class UnlockLevelProvider {

    private final SystemSettingService systemSettingService;

    public int getLevel(UnlockFeature feature) {
        int level = systemSettingService.findIntValue(feature.getSettingKey()).orElse(feature.getDefaultLevel());
        return level >= 1 ? level : feature.getDefaultLevel();
    }
}
