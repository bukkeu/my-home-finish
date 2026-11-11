package com.wanted.moneybeetle.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import java.util.OptionalInt;

/* comment.
*   담당 : PM (#266)
*   해금 레벨 운영 설정(UNLOCK_LEVEL_TRADING·UNLOCK_LEVEL_HOUSING)을 흉내 내는 UnlockLevelProvider 를 만든다 (단위 테스트용)
*  */
public final class UnlockLevelFixture {

    private UnlockLevelFixture() {
    }

    /** 설정이 없는 것처럼 - 기본값(모의투자 8, 모의 청약 12) */
    public static UnlockLevelProvider defaults() {
        return of(null, null);
    }

    /** 모의투자·모의 청약 해금 레벨 설정을 정한다 (null 이면 설정 없음 → 기본값) */
    public static UnlockLevelProvider of(Integer tradingLevel, Integer housingLevel) {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.findIntValue("UNLOCK_LEVEL_TRADING"))
                .thenReturn(tradingLevel == null ? OptionalInt.empty() : OptionalInt.of(tradingLevel));
        when(settings.findIntValue("UNLOCK_LEVEL_HOUSING"))
                .thenReturn(housingLevel == null ? OptionalInt.empty() : OptionalInt.of(housingLevel));
        return new UnlockLevelProvider(settings);
    }
}
