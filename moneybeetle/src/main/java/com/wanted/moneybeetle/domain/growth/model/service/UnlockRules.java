package com.wanted.moneybeetle.domain.growth.model.service;

import java.util.ArrayList;
import java.util.List;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   레벨로 열리는 기능 (SP-24, GRW-08): 모의투자 Lv.8, 모의 청약 Lv.12 (초안)
*   - 실제 해금 레벨은 운영 설정(system_setting 의 UNLOCK_LEVEL_TRADING·UNLOCK_LEVEL_HOUSING, 기본 8·12)이다.
*     레벨 정책(API 24 unlocks)·레벨업 해금 알림·공통 레이아웃은 UnlockLevelProvider 로 읽은 값을 이 클래스의 메서드에 넘긴다 (#266).
*     아래 상수(TRADING_LEVEL·HOUSING_LEVEL)는 설정이 기본값일 때의 값이다. 홈·내 정보(#238)가 설정을 읽게 되면 없앨 수 있다
*   - 이름은 API 명세서 24의 unlocks 값(TRADING, HOUSING)을 쓴다
*  */
public final class UnlockRules {

    public static final String TRADING = "TRADING";
    public static final String HOUSING = "HOUSING";

    public static final int TRADING_LEVEL = 8;
    public static final int HOUSING_LEVEL = 12;

    private UnlockRules() {
    }

    /** 이 레벨에 올라서 새로 열리는 기능 (없으면 빈 목록). 해금 레벨은 운영 설정에서 읽은 값을 넘긴다 */
    public static List<String> unlockedAt(int level, int tradingLevel, int housingLevel) {
        return unlockedBetween(level - 1, level, tradingLevel, housingLevel);
    }

    /**
     * fromLevel 다음부터 toLevel 까지 오르며 새로 열린 기능 (여러 레벨이 한 번에 올라도 한 번씩, 빠짐없이).
     * 기능마다 "올리기 전에는 해금 레벨에 못 미쳤고(fromLevel < 해금 레벨), 올린 뒤에는 닿았을 때(해금 레벨 <= toLevel)"만 연다.
     * 해금 레벨이 낮은 기능부터 나열하고, 같으면 모의투자가 먼저다
     */
    public static List<String> unlockedBetween(int fromLevel, int toLevel, int tradingLevel, int housingLevel) {
        List<String> unlocked = new ArrayList<>();
        if (tradingLevel <= housingLevel) {
            addIfOpened(unlocked, TRADING, tradingLevel, fromLevel, toLevel);
            addIfOpened(unlocked, HOUSING, housingLevel, fromLevel, toLevel);
        } else {
            addIfOpened(unlocked, HOUSING, housingLevel, fromLevel, toLevel);
            addIfOpened(unlocked, TRADING, tradingLevel, fromLevel, toLevel);
        }
        return unlocked;
    }

    private static void addIfOpened(List<String> unlocked, String feature, int unlockLevel, int fromLevel, int toLevel) {
        if (fromLevel < unlockLevel && unlockLevel <= toLevel) {
            unlocked.add(feature);
        }
    }
}
