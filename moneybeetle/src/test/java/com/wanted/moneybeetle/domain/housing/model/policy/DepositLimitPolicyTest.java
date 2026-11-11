package com.wanted.moneybeetle.domain.housing.model.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : SCM (모의 청약)
*   #327 청약통장 납입 한도를 운영 설정에서 읽는다 (SystemSettingService 는 가짜, 비워 두면 설정이 없는 것)
*   - 설정이 있으면 그 값, 없거나 숫자가 아니면 그 값만 기본값
*   - 1 미만이거나 최소 > 최대처럼 규칙이 뒤집히면 세 값 모두 기본값
*  */
class DepositLimitPolicyTest {

    private SystemSettingService settings;
    private DepositLimitPolicy policy;

    @BeforeEach
    void setUp() {
        settings = mock(SystemSettingService.class);
        policy = new DepositLimitPolicy(settings);
    }

    private void given(String key, int value) {
        when(settings.findIntValue(key)).thenReturn(OptionalInt.of(value));
    }

    @Test
    void 설정이_없으면_기본값_20000_500000_250000() {
        assertThat(policy.getLimits()).isEqualTo(DepositLimits.DEFAULT);
        assertThat(DepositLimits.DEFAULT).isEqualTo(new DepositLimits(20_000, 500_000, 250_000));
    }

    @Test
    void 관리자가_바꾼_값을_그대로_쓴다() {
        given("DEPOSIT_MIN_AMOUNT", 30_000);
        given("DEPOSIT_MAX_AMOUNT", 400_000);
        given("DEPOSIT_DAILY_RECOGNIZED_LIMIT", 100_000);

        assertThat(policy.getLimits()).isEqualTo(new DepositLimits(30_000, 400_000, 100_000));
    }

    @Test
    void 하나만_있으면_나머지는_기본값() {
        given("DEPOSIT_MAX_AMOUNT", 1_000_000);

        assertThat(policy.getLimits()).isEqualTo(new DepositLimits(20_000, 1_000_000, 250_000));
    }

    @Test
    void 최소가_최대보다_크거나_1_미만이면_세_값_모두_기본값() {
        given("DEPOSIT_MIN_AMOUNT", 600_000);                 // 최대 500,000 보다 크다
        assertThat(policy.getLimits()).isEqualTo(DepositLimits.DEFAULT);

        given("DEPOSIT_MIN_AMOUNT", 20_000);
        given("DEPOSIT_DAILY_RECOGNIZED_LIMIT", 0);           // 인정금액이 쌓이지 않게 된다
        assertThat(policy.getLimits()).isEqualTo(DepositLimits.DEFAULT);
    }
}
