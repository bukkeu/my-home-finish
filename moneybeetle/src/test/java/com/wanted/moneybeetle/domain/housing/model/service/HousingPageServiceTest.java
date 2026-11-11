package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.housing.model.dto.HousingPageDTO;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingPageService #58 확인 : 레벨·해금 레벨·잠금 여부·오늘(서울)
*   - 해금 레벨은 운영 설정 UNLOCK_LEVEL_HOUSING (#223). 설정이 없으면 기본 Lv.12
*  */
class HousingPageServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final GrowthService growthService = mock(GrowthService.class);
    private final SystemSettingService systemSettingService = mock(SystemSettingService.class);   // 기본은 설정 없음
    private final HousingPageService service = new HousingPageService(growthService,
            new UnlockLevelProvider(systemSettingService),
            Clock.fixed(TODAY.atStartOfDay(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL));

    @Test
    void Lv11_은_잠겨_있다() {
        when(growthService.getLevel(8L)).thenReturn(11);

        HousingPageDTO page = service.getPage(8L);

        assertThat(page.getLevel()).isEqualTo(11);
        assertThat(page.getUnlockLevel()).isEqualTo(12);
        assertThat(page.isLocked()).isTrue();
        assertThat(page.getToday()).isEqualTo(TODAY);
    }

    @Test
    void Lv12_부터_열린다() {
        when(growthService.getLevel(8L)).thenReturn(12);

        assertThat(service.getPage(8L).isLocked()).isFalse();
    }

    @Test
    void 관리자가_해금_레벨을_10으로_바꾸면_Lv10_부터_열리고_화면에도_10이_보인다() {
        when(systemSettingService.findIntValue("UNLOCK_LEVEL_HOUSING")).thenReturn(OptionalInt.of(10));
        when(growthService.getLevel(8L)).thenReturn(10);

        HousingPageDTO page = service.getPage(8L);

        assertThat(page.getUnlockLevel()).isEqualTo(10);
        assertThat(page.isLocked()).isFalse();
        assertThat(service.unlockLevel()).isEqualTo(10);   // 관리자 운영 화면 안내 문구
    }
}
