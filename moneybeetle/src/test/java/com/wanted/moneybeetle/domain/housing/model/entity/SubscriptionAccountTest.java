package com.wanted.moneybeetle.domain.housing.model.entity;

import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.policy.DepositLimits;
import com.wanted.moneybeetle.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/* comment.
*   담당 : SCM (모의 청약)
*   #291 납입 규칙은 통장이 지킨다 : Service 를 거치지 않고 SubscriptionAccount.deposit 을 바로 불러도 규칙이 깨지지 않는다
*   - 1회 20,000 ~ 500,000, 하루 1회, 인정금액은 min(금액, 250,000)
*   - 규칙을 어기면 예외이고 통장 값은 하나도 바뀌지 않는다
*  */
class SubscriptionAccountTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final DepositLimits LIMITS = DepositLimits.DEFAULT;   // 20,000 / 500,000 / 250,000

    private static void assertRejected(Runnable action, HousingErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static void assertUntouched(SubscriptionAccount account) {
        assertThat(account.getBalance()).isZero();
        assertThat(account.getRecognizedAmount()).isZero();
        assertThat(account.getPaidCount()).isZero();
        assertThat(account.getLastPaidDate()).isNull();
    }

    @Test
    void 한도_안의_금액은_잔액에_전액_인정금액에는_하루_한도까지_더하고_인정금액을_돌려준다() {
        SubscriptionAccount account = SubscriptionAccount.create(8L);

        long recognized = account.deposit(300_000L, TODAY, LIMITS);

        assertThat(recognized).isEqualTo(250_000L);
        assertThat(account.getBalance()).isEqualTo(300_000L);
        assertThat(account.getRecognizedAmount()).isEqualTo(250_000L);
        assertThat(account.getPaidCount()).isEqualTo(1);
        assertThat(account.getLastPaidDate()).isEqualTo(TODAY);
    }

    @Test
    void 경계값_20000과_500000은_납입된다() {
        SubscriptionAccount min = SubscriptionAccount.create(8L);
        SubscriptionAccount max = SubscriptionAccount.create(9L);

        assertThat(min.deposit(LIMITS.minAmount(), TODAY, LIMITS)).isEqualTo(20_000L);
        assertThat(max.deposit(LIMITS.maxAmount(), TODAY, LIMITS)).isEqualTo(LIMITS.dailyRecognizedLimit());
    }

    @Test
    void 한도_밖_금액이면_예외이고_통장은_그대로다() {
        SubscriptionAccount account = SubscriptionAccount.create(8L);

        assertRejected(() -> account.deposit(19_999L, TODAY, LIMITS), HousingErrorCode.DEPOSIT_BELOW_MIN);
        assertRejected(() -> account.deposit(500_001L, TODAY, LIMITS), HousingErrorCode.DEPOSIT_ABOVE_MAX);
        assertUntouched(account);
    }

    @Test
    void 운영_설정으로_바꾼_한도를_쓰고_오류_문구에도_그_금액이_들어간다() {   // #327
        DepositLimits custom = new DepositLimits(30_000, 400_000, 100_000);
        SubscriptionAccount account = SubscriptionAccount.create(8L);

        assertThatThrownBy(() -> account.deposit(29_999L, TODAY, custom))
                .isInstanceOf(BusinessException.class)
                .hasMessage("최소 30,000 캐시부터 납입할 수 있어요");
        assertThatThrownBy(() -> account.deposit(400_001L, TODAY, custom))
                .isInstanceOf(BusinessException.class)
                .hasMessage("한 번에 최대 400,000 캐시까지 납입할 수 있어요");
        assertUntouched(account);

        assertThat(account.deposit(300_000L, TODAY, custom)).isEqualTo(100_000L);   // 인정금액은 하루 한도 100,000 까지
        assertThat(account.getBalance()).isEqualTo(300_000L);
    }

    @Test
    void 같은_날_두_번_납입하면_예외이고_첫_납입만_남는다() {
        SubscriptionAccount account = SubscriptionAccount.create(8L);
        account.deposit(100_000L, TODAY, LIMITS);

        assertRejected(() -> account.deposit(100_000L, TODAY, LIMITS), HousingErrorCode.ALREADY_PAID_TODAY);
        assertThat(account.getBalance()).isEqualTo(100_000L);
        assertThat(account.getPaidCount()).isEqualTo(1);

        // 다음 날은 다시 납입할 수 있다
        account.deposit(100_000L, TODAY.plusDays(1), LIMITS);
        assertThat(account.getPaidCount()).isEqualTo(2);
    }
}
