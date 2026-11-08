package com.wanted.moneybeetle.domain.growth.model.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   LevelTable · UnlockRules (이슈 #11) - 값만으로 판단하므로 서버·DB 없이 돈다
*  */
class LevelTableTest {

    private final LevelTable table = LevelTableFixture.table();

    @Test
    @DisplayName("확정값: 누적 경험치 Lv.8 = 1,250, Lv.12 = 2,450 이고 최대 Lv.20 은 6,250")
    void confirmedThresholds() {
        assertThat(table.policyOf(1).getRequiredExp()).isZero();
        assertThat(table.policyOf(4).getRequiredExp()).isEqualTo(450);
        assertThat(table.policyOf(8).getRequiredExp()).isEqualTo(1_250);
        assertThat(table.policyOf(12).getRequiredExp()).isEqualTo(2_450);
        assertThat(table.policyOf(17).getRequiredExp()).isEqualTo(4_450);
        assertThat(table.policyOf(20).getRequiredExp()).isEqualTo(6_250);
        assertThat(table.maxLevel()).isEqualTo(20);
    }

    @Test
    @DisplayName("levelFor: 경계값에서 레벨이 바뀌고, 최대 레벨을 넘는 경험치는 Lv.20")
    void levelForBoundaries() {
        assertThat(table.levelFor(0)).isEqualTo(1);
        assertThat(table.levelFor(149)).isEqualTo(1);
        assertThat(table.levelFor(150)).isEqualTo(2);
        assertThat(table.levelFor(1_249)).isEqualTo(7);
        assertThat(table.levelFor(1_250)).isEqualTo(8);
        assertThat(table.levelFor(2_450)).isEqualTo(12);
        assertThat(table.levelFor(6_249)).isEqualTo(19);
        assertThat(table.levelFor(6_250)).isEqualTo(20);
        assertThat(table.levelFor(999_999)).isEqualTo(20);
    }

    @Test
    @DisplayName("칭호는 구간별이다 (고시원 1~3, 원룸 4~7, 투룸 8~11, 아파트 12~16, 내 집 17~20)")
    void titles() {
        assertThat(table.titleOf(1)).isEqualTo("고시원");
        assertThat(table.titleOf(3)).isEqualTo("고시원");
        assertThat(table.titleOf(4)).isEqualTo("원룸");
        assertThat(table.titleOf(8)).isEqualTo("투룸");
        assertThat(table.titleOf(12)).isEqualTo("아파트");
        assertThat(table.titleOf(17)).isEqualTo("내 집");
        assertThat(table.titleOf(20)).isEqualTo("내 집");
    }

    @Test
    @DisplayName("레벨업 캐시는 새 레벨 × 10,000 이고 여러 레벨이 한 번에 오르면 레벨마다 모두 더한다 (SP-23)")
    void levelUpCash() {
        assertThat(table.policyOf(1).getLevelupCash()).isZero();
        assertThat(table.policyOf(8).getLevelupCash()).isEqualTo(80_000);
        assertThat(table.levelUpCashBetween(2, 3)).isEqualTo(30_000);
        assertThat(table.levelUpCashBetween(2, 5)).isEqualTo(30_000 + 40_000 + 50_000);
        assertThat(table.levelUpCashBetween(5, 5)).isZero();
    }

    @Test
    @DisplayName("범위 밖 레벨은 IllegalArgumentException")
    void outOfRange() {
        assertThatThrownBy(() -> table.policyOf(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> table.policyOf(21)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 레벨업 캐시 정책은 해당 레벨 정보를 포함해 거절한다")
    void rejectsNegativeLevelUpCash() {
        assertThatThrownBy(() -> new LevelTable(List.of(
                LevelPolicy.create(1, 0, "고시원", 0),
                LevelPolicy.create(2, 150, "고시원", -1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lv.2");
    }

    @Test
    @DisplayName("잘못된 정책표는 만들 때 거절한다 (빈 표, 레벨 빠짐, Lv.1 이 0이 아님, 경험치가 늘지 않음)")
    void invalidTables() {
        assertThatThrownBy(() -> new LevelTable(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LevelTable(List.of(
                LevelPolicy.create(1, 0, "고시원", 0), LevelPolicy.create(3, 300, "고시원", 30_000))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LevelTable(List.of(LevelPolicy.create(1, 10, "고시원", 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LevelTable(List.of(
                LevelPolicy.create(1, 0, "고시원", 0), LevelPolicy.create(2, 0, "고시원", 20_000))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("해금: Lv.8 에서 모의투자, Lv.12 에서 모의 청약이 열리고 여러 레벨을 건너뛰어도 빠지지 않는다")
    void unlocks() {
        assertThat(UnlockRules.unlockedAt(8)).containsExactly(UnlockRules.TRADING);
        assertThat(UnlockRules.unlockedAt(12)).containsExactly(UnlockRules.HOUSING);
        assertThat(UnlockRules.unlockedAt(5)).isEmpty();
        assertThat(UnlockRules.unlockedBetween(6, 9)).containsExactly(UnlockRules.TRADING);
        assertThat(UnlockRules.unlockedBetween(7, 13)).containsExactly(UnlockRules.TRADING, UnlockRules.HOUSING);
        assertThat(UnlockRules.unlockedBetween(8, 11)).isEmpty();
    }
}
