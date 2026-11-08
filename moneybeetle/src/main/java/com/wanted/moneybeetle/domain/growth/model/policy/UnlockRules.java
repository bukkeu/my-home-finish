package com.wanted.moneybeetle.domain.growth.model.policy;

import java.util.ArrayList;
import java.util.List;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   레벨로 열리는 기능 (SP-24, GRW-08): 모의투자 Lv.8, 모의 청약 Lv.12 (초안)
*   - 값은 system_setting(UNLOCK_LEVEL_TRADING 등)이 기준이지만, 설정 서비스가 아직 없어 여기에 기본값을 둔다.
*     SystemSettingService 가 생기면 이 값을 거기서 읽도록 바꾼다
*   - 이름은 API 명세서 24의 unlocks 값(TRADING, HOUSING)을 쓴다
*  */
public final class UnlockRules {

    public static final String TRADING = "TRADING";
    public static final String HOUSING = "HOUSING";

    public static final int TRADING_LEVEL = 8;
    public static final int HOUSING_LEVEL = 12;

    private UnlockRules() {
    }

    /** 이 레벨에 올라서 새로 열리는 기능 (없으면 빈 목록) */
    public static List<String> unlockedAt(int level) {
        List<String> unlocked = new ArrayList<>();
        if (level == TRADING_LEVEL) {
            unlocked.add(TRADING);
        }
        if (level == HOUSING_LEVEL) {
            unlocked.add(HOUSING);
        }
        return unlocked;
    }

    /** fromLevel 다음부터 toLevel 까지 오르며 새로 열린 기능 (여러 레벨이 한 번에 올라도 빠짐없이) */
    public static List<String> unlockedBetween(int fromLevel, int toLevel) {
        List<String> unlocked = new ArrayList<>();
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            unlocked.addAll(unlockedAt(level));
        }
        return unlocked;
    }
}
