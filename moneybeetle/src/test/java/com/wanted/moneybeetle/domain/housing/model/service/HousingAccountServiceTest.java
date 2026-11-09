package com.wanted.moneybeetle.domain.housing.model.service;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.housing.exception.HousingErrorCode;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingApplicationRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.HousingComplexRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionAccountRepository;
import com.wanted.moneybeetle.domain.housing.model.dao.SubscriptionDepositRepository;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.DepositResultDTO;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionAccount;
import com.wanted.moneybeetle.domain.housing.model.entity.SubscriptionDeposit;
import com.wanted.moneybeetle.global.config.TimeConfig;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (모의 청약)
*   청약통장 #28 확인 (DB 없이 Repository·RewardService 는 가짜, 오늘은 2026-11-05 로 고정)
*   - 통장 개설 : 없으면 만들고, 이미 있으면 그대로
*   - 납입 : 금액 범위, 하루 1회, 인정금액 = min(금액, 250,000), 회차 +1, 캐시 차감
*  */
class HousingAccountServiceTest {

    private static final Long MEMBER_ID = 8L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private SubscriptionAccountRepository accountRepository;
    private SubscriptionDepositRepository depositRepository;
    private RewardService rewardService;
    private HousingService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(SubscriptionAccountRepository.class);
        depositRepository = mock(SubscriptionDepositRepository.class);
        rewardService = mock(RewardService.class);
        Clock fixed = Clock.fixed(TODAY.atStartOfDay(TimeConfig.SEOUL).toInstant(), TimeConfig.SEOUL);
        service = new HousingService(mock(HousingComplexRepository.class), mock(HousingApplicationRepository.class),
                fixed, accountRepository, depositRepository, rewardService);

        // 저장하면 번호(id)가 매겨진 것처럼 돌려준다
        when(depositRepository.save(any(SubscriptionDeposit.class))).thenAnswer(call -> {
            SubscriptionDeposit deposit = call.getArgument(0);
            ReflectionTestUtils.setField(deposit, "id", 15L);
            return deposit;
        });
    }

    // 잔액 1,840,000 / 인정 1,590,000 / 14회차 / 마지막 납입일 지정
    private SubscriptionAccount account(LocalDate lastPaidDate) {
        SubscriptionAccount account = SubscriptionAccount.create(MEMBER_ID);
        ReflectionTestUtils.setField(account, "id", 1L);
        ReflectionTestUtils.setField(account, "balance", 1_840_000L);
        ReflectionTestUtils.setField(account, "recognizedAmount", 1_590_000L);
        ReflectionTestUtils.setField(account, "paidCount", 14);
        ReflectionTestUtils.setField(account, "lastPaidDate", lastPaidDate);
        return account;
    }

    private void assertRejected(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void 통장은_없으면_새로_만들고_있으면_그대로_둔다() {
        when(accountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        service.openAccount(MEMBER_ID);
        verify(accountRepository).save(any(SubscriptionAccount.class));

        SubscriptionAccount created = SubscriptionAccount.create(MEMBER_ID);
        assertThat(created.getBalance()).isZero();
        assertThat(created.getPaidCount()).isZero();
        assertThat(created.getLastPaidDate()).isNull();

        SubscriptionAccountRepository another = mock(SubscriptionAccountRepository.class);
        when(another.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(account(null)));
        new HousingService(null, null, null, another, null, null).openAccount(MEMBER_ID);
        verify(another, never()).save(any());
    }

    @Test
    void 내_통장은_오늘_납입_여부와_캐시를_함께_보여준다() {
        when(accountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(account(TODAY)));
        when(rewardService.getBalance(MEMBER_ID)).thenReturn(1_250_000L);

        AccountDTO dto = service.getAccount(MEMBER_ID);

        assertThat(dto.isPaidToday()).isTrue();
        assertThat(dto.getPaidCount()).isEqualTo(14);
        assertThat(dto.getCash()).isEqualTo(1_250_000L);
        assertThat(dto.getDepositPolicy().getDailyRecognizedLimit()).isEqualTo(250_000L);
    }

    @Test
    void 납입하면_잔액은_전액_인정금액은_250000까지_더하고_회차가_1_늘어난다() {
        SubscriptionAccount account = account(LocalDate.of(2026, 11, 4));
        when(accountRepository.findWithLockByMemberId(MEMBER_ID)).thenReturn(Optional.of(account));
        when(rewardService.spendCash(eq(MEMBER_ID), eq(CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT), eq(15L), eq(300_000L)))
                .thenReturn(950_000L);

        DepositResultDTO result = service.deposit(MEMBER_ID, 300_000L);

        assertThat(result.getDepositId()).isEqualTo(15L);
        assertThat(result.getAmount()).isEqualTo(300_000L);
        assertThat(result.getRecognizedAmount()).isEqualTo(250_000L);          // min(300,000, 250,000)
        assertThat(result.getBalance()).isEqualTo(2_140_000L);                 // 1,840,000 + 300,000
        assertThat(result.getTotalRecognizedAmount()).isEqualTo(1_840_000L);   // 1,590,000 + 250,000
        assertThat(result.getPaidCount()).isEqualTo(15);                       // 14 + 1
        assertThat(result.getCash()).isEqualTo(950_000L);
        assertThat(account.getLastPaidDate()).isEqualTo(TODAY);
    }

    @Test
    void 같은_날_두_번째_납입은_ALREADY_PAID_TODAY() {
        when(accountRepository.findWithLockByMemberId(MEMBER_ID)).thenReturn(Optional.of(account(TODAY)));

        assertRejected(() -> service.deposit(MEMBER_ID, 100_000L), HousingErrorCode.ALREADY_PAID_TODAY);
        verify(rewardService, never()).spendCash(any(), anyString(), any(), anyLong());
    }

    @Test
    void 금액_범위는_20000_이상_500000_이하만() {
        assertRejected(() -> service.deposit(MEMBER_ID, 19_999L), HousingErrorCode.DEPOSIT_BELOW_MIN);
        assertRejected(() -> service.deposit(MEMBER_ID, 500_001L), HousingErrorCode.DEPOSIT_ABOVE_MAX);

        // 경계값은 통과 (여기서는 통장까지 가는지만 본다)
        when(accountRepository.findWithLockByMemberId(MEMBER_ID)).thenReturn(Optional.of(account(null)));
        assertThat(service.deposit(MEMBER_ID, 20_000L).getRecognizedAmount()).isEqualTo(20_000L);
    }

    @Test
    void 캐시가_부족하면_RewardService_의_NOT_ENOUGH_CASH_가_그대로_나가고_통장은_바뀌지_않는다() {
        SubscriptionAccount account = account(LocalDate.of(2026, 11, 4));
        when(accountRepository.findWithLockByMemberId(MEMBER_ID)).thenReturn(Optional.of(account));
        when(rewardService.spendCash(any(), anyString(), any(), anyLong()))
                .thenThrow(new BusinessException(GrowthErrorCode.NOT_ENOUGH_CASH));

        assertRejected(() -> service.deposit(MEMBER_ID, 300_000L), GrowthErrorCode.NOT_ENOUGH_CASH);
        assertThat(account.getPaidCount()).isEqualTo(14);          // 통장 반영 전에 멈춘다 (저장된 내역은 트랜잭션이 취소)
        assertThat(account.getBalance()).isEqualTo(1_840_000L);
    }
}
