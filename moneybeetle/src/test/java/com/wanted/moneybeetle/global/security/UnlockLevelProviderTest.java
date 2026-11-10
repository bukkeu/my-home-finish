package com.wanted.moneybeetle.global.security;

import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   UnlockLevelProvider 확인 (#223)
*   - 모의 청약 : 운영 설정(UNLOCK_LEVEL_HOUSING) 값을 쓴다. 없거나 정수가 아니면(빈 값) 또는 1 미만이면 기본 Lv.12
*   - 모의투자 : 아직 설정을 읽지 않고 기본 Lv.8 (시즌 해금 레벨과의 기준을 정한 뒤 바꾼다)
*  */
class UnlockLevelProviderTest {

    private final SystemSettingService systemSettingService = mock(SystemSettingService.class);
    private final UnlockLevelProvider provider = new UnlockLevelProvider(systemSettingService);

    @Test
    void 모의청약은_운영_설정_값을_쓴다() {
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.of(10));

        assertThat(provider.getLevel(UnlockFeature.HOUSING)).isEqualTo(10);
    }

    @Test
    void 설정이_없거나_정수가_아니면_기본값() {
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.empty());

        assertThat(provider.getLevel(UnlockFeature.HOUSING)).isEqualTo(12);
    }

    @Test
    void 설정이_1_미만이면_기본값() {
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.of(0));
        assertThat(provider.getLevel(UnlockFeature.HOUSING)).isEqualTo(12);

        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.of(-3));
        assertThat(provider.getLevel(UnlockFeature.HOUSING)).isEqualTo(12);
    }

    @Test
    void 모의투자는_설정을_읽지_않고_기본값() {
        assertThat(provider.getLevel(UnlockFeature.TRADING)).isEqualTo(8);
        verify(systemSettingService, never()).findIntValue(any());
    }
}
