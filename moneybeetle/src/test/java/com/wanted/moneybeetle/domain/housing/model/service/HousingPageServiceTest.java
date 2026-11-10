package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.housing.model.dto.HousingPageDTO;
import com.wanted.moneybeetle.global.config.TimeConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   HousingPageService #58 확인 : 레벨·해금 레벨(12)·잠금 여부·오늘(서울)
*  */
class HousingPageServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final GrowthService growthService = mock(GrowthService.class);
    private final HousingPageService service = new HousingPageService(growthService,
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
}
