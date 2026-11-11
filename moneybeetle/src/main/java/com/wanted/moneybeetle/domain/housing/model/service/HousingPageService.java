package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.housing.model.dto.HousingPageDTO;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   모의 청약 화면의 첫 값 (#58)
*   - 해금 레벨은 레벨 검사(LevelCheckInterceptor)와 같은 곳(UnlockLevelProvider)에서 읽는다 (#223, 운영 설정 UNLOCK_LEVEL_HOUSING)
*     → 관리자가 설정을 바꿔도 화면의 잠금 표시와 API 의 LEVEL_LOCKED 가 항상 같은 기준이다
*   - 레벨은 성장 도메인 Service(GrowthService.getLevel)로만 읽는다 (역할분담 §7)
*  */
@Service
@RequiredArgsConstructor
public class HousingPageService {

    private final GrowthService growthService;
    private final UnlockLevelProvider unlockLevelProvider;
    private final Clock clock;

    public HousingPageDTO getPage(Long memberId) {
        int level = growthService.getLevel(memberId);
        int unlockLevel = unlockLevelProvider.getLevel(UnlockFeature.HOUSING);
        return new HousingPageDTO(level, unlockLevel, level < unlockLevel, LocalDate.now(clock));
    }

    // 지금 적용 중인 모의 청약 해금 레벨 (관리자 운영 화면 안내 문구용, #223)
    public int unlockLevel() {
        return unlockLevelProvider.getLevel(UnlockFeature.HOUSING);
    }

    // 관리자 운영 화면(#63)이 산정·발표 버튼을 켤지 정할 때 쓰는 "오늘" (서버와 같은 서울 날짜. 서버도 같은 기준으로 다시 막는다)
    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
