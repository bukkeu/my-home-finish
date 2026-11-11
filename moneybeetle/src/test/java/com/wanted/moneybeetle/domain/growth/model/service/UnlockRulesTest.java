package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (#266)
*   UnlockRules 확인 : 해금 레벨(운영 설정)에서만 기능이 열린 것으로 본다 (SP-24)
*  */
class UnlockRulesTest {

    @Test
    @DisplayName("기본 해금 레벨(모의투자 8, 청약 12)에서는 그 레벨에서만 열린다")
    void defaultLevels() {
        assertThat(UnlockRules.unlockedAt(8, 8, 12)).containsExactly(UnlockRules.TRADING);
        assertThat(UnlockRules.unlockedAt(12, 8, 12)).containsExactly(UnlockRules.HOUSING);
        assertThat(UnlockRules.unlockedAt(7, 8, 12)).isEmpty();
        assertThat(UnlockRules.unlockedAt(9, 8, 12)).isEmpty();
    }

    @Test
    @DisplayName("청약 설정이 8·15 로 바뀌면 청약은 Lv.12 가 아니라 설정 레벨에서만 열린다")
    void housingSettingChanged() {
        for (int housing : new int[] {8, 15}) {
            for (int level = 1; level <= 20; level++) {
                boolean opened = UnlockRules.unlockedAt(level, 8, housing).contains(UnlockRules.HOUSING);
                assertThat(opened).as("청약 설정 %d, Lv.%d", housing, level).isEqualTo(level == housing);
            }
        }
    }

    @Test
    @DisplayName("모의투자 설정이 6·10 으로 바뀌면 모의투자는 설정 레벨에서만 열린다")
    void tradingSettingChanged() {
        for (int trading : new int[] {6, 10}) {
            for (int level = 1; level <= 20; level++) {
                boolean opened = UnlockRules.unlockedAt(level, trading, 12).contains(UnlockRules.TRADING);
                assertThat(opened).as("모의투자 설정 %d, Lv.%d", trading, level).isEqualTo(level == trading);
            }
        }
    }

    @Test
    @DisplayName("여러 레벨을 한 번에 올라도 기능마다 한 번만, 해금 레벨이 낮은 것부터 연다")
    void unlockedBetweenOnce() {
        assertThat(UnlockRules.unlockedBetween(1, 20, 8, 12)).containsExactly(UnlockRules.TRADING, UnlockRules.HOUSING);
        assertThat(UnlockRules.unlockedBetween(1, 20, 10, 6)).containsExactly(UnlockRules.HOUSING, UnlockRules.TRADING);
        assertThat(UnlockRules.unlockedBetween(1, 7, 8, 12)).isEmpty();
        assertThat(UnlockRules.unlockedBetween(8, 11, 8, 12)).as("이미 연 모의투자는 다시 열지 않는다").isEmpty();
        assertThat(UnlockRules.unlockedBetween(8, 12, 8, 12)).containsExactly(UnlockRules.HOUSING);
        assertThat(UnlockRules.unlockedBetween(5, 5, 8, 12)).as("오르지 않으면 아무것도 열지 않는다").isEmpty();
    }

    @Test
    @DisplayName("두 기능의 해금 레벨이 같으면 한 번에 둘 다, 모의투자가 먼저")
    void sameLevel() {
        assertThat(UnlockRules.unlockedAt(9, 9, 9)).containsExactly(UnlockRules.TRADING, UnlockRules.HOUSING);
    }
}
