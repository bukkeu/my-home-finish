package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.housing.model.dto.HousingPageDTO;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   모의 청약 화면의 첫 값 (#58)
*   - 해금 레벨은 레벨 검사(LevelCheckInterceptor)와 같은 값(UnlockFeature.HOUSING)을 쓴다
*     → 화면의 잠금 표시와 API 의 LEVEL_LOCKED 가 항상 같은 기준이다
*   - 레벨은 성장 도메인 Service(GrowthService.getLevel)로만 읽는다 (역할분담 §7)
*  */
@Service
@RequiredArgsConstructor
public class HousingPageService {

    private final GrowthService growthService;
    private final Clock clock;

    public HousingPageDTO getPage(Long memberId) {
        int level = growthService.getLevel(memberId);
        int unlockLevel = UnlockFeature.HOUSING.getDefaultLevel();
        return new HousingPageDTO(level, unlockLevel, level < unlockLevel, LocalDate.now(clock));
    }
}
