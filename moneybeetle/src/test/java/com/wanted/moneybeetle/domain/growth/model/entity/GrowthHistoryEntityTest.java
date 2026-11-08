package com.wanted.moneybeetle.domain.growth.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (성장)
*   보상 규칙·캐시 내역·경험치 내역 Entity 의 규칙 (SP-15, SP-22) - 서버·DB 없이 돈다
*  */
class GrowthHistoryEntityTest {

    @Test
    @DisplayName("적립은 양수로, 사용은 음수로 기록된다")
    void earnIsPositiveAndUseIsNegative() {
        CashHistory earn = CashHistory.earn(1L, CashHistory.SOURCE_COURSE_COMPLETE, 15L, 2L, 3000, 12400);
        CashHistory use = CashHistory.use(1L, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT, 7L, 1000, 11400);

        assertThat(earn.getAmount()).isEqualTo(3000);
        assertThat(earn.getHistoryType()).isEqualTo(CashHistory.EARN);
        assertThat(earn.isIncome()).isTrue();
        assertThat(use.getAmount()).isEqualTo(-1000);
        assertThat(use.getHistoryType()).isEqualTo(CashHistory.USE);
        assertThat(use.isIncome()).isFalse();
    }

    @Test
    @DisplayName("잔액은 0 미만이 될 수 없다 (SP-15)")
    void balanceCannotBeNegative() {
        assertThatThrownBy(() -> CashHistory.use(1L, CashHistory.SOURCE_SEASON_JOIN, 1L, 500, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0원 이하 금액은 기록할 수 없다")
    void amountMustBePositive() {
        assertThatThrownBy(() -> CashHistory.earn(1L, CashHistory.SOURCE_ATTENDANCE, 1L, 1L, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashHistory.use(1L, CashHistory.SOURCE_SEASON_JOIN, 1L, -5, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("관리자 지급·회수는 사유가 필수다")
    void adminAdjustRequiresReason() {
        assertThatThrownBy(() -> CashHistory.adminGive(1L, 99L, 1000, 1000, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashHistory.adminTake(1L, 99L, 1000, 0, null))
                .isInstanceOf(IllegalArgumentException.class);

        CashHistory take = CashHistory.adminTake(1L, 99L, 1000, 500, "오지급 회수");
        assertThat(take.getAmount()).isEqualTo(-1000);
        assertThat(take.getSourceType()).isEqualTo(CashHistory.SOURCE_ADMIN);
        assertThat(take.getSourceId()).isNull();
    }

    @Test
    @DisplayName("경험치 내역은 지급량 > 0, 누적 >= 지급량, 레벨 1~20 이어야 한다")
    void expHistoryRules() {
        ExpHistory ok = ExpHistory.create(1L, ExpHistory.SOURCE_COURSE_COMPLETE, 15L, 3L, 100, 1120, 8);
        assertThat(ok.getAmount()).isEqualTo(100);
        assertThat(ok.getLevelAfter()).isEqualTo(8);

        assertThatThrownBy(() -> ExpHistory.create(1L, ExpHistory.SOURCE_COURSE_COMPLETE, 15L, 3L, 0, 100, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpHistory.create(1L, ExpHistory.SOURCE_COURSE_COMPLETE, 15L, 3L, 100, 50, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpHistory.create(1L, ExpHistory.SOURCE_COURSE_COMPLETE, 15L, 3L, 100, 100, 21))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보상 규칙은 음수 값을 가질 수 없고, 둘 다 0이면 지급할 것이 없다")
    void rewardRuleRules() {
        RewardRule rule = RewardRule.create(RewardRule.COURSE_COMPLETE, "강의 학습 완료", 3000, 100, "강의 완료 시");
        assertThat(rule.isActive()).isTrue();
        assertThat(rule.hasReward()).isTrue();

        rule.changeAmounts(0, 0);
        assertThat(rule.hasReward()).isFalse();
        assertThatThrownBy(() -> rule.changeAmounts(-1, 0)).isInstanceOf(IllegalArgumentException.class);

        rule.deactivate();
        assertThat(rule.isActive()).isFalse();
    }
}
