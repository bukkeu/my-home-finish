package com.wanted.moneybeetle.domain.growth.model.policy;

import java.util.ArrayList;
import java.util.List;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   레벨로 열리는 기능 (SP-24, GRW-08): 모의투자 Lv.8, 모의 청약 Lv.12 (초안)
*   - 운영 기준 값은 system_setting 의 UNLOCK_LEVEL_TRADING(8)·UNLOCK_LEVEL_HOUSING(12)이다 (schema.sql·시드에 있다).
*     그러나 SystemSetting Entity·SystemSettingService 는 아직 빈 껍데기(operation 도메인, SCM 담당)라서 읽을 수 없어
*     여기에 같은 값을 상수로 둔다. 이 상수는 설정이 바뀌어도 따라가지 못하므로 임시다.
*     TODO(후속, SCM에 요청): SystemSettingService 에 정수 설정 조회가 생기면 이 클래스가 그 값을 읽도록 바꾼다
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
