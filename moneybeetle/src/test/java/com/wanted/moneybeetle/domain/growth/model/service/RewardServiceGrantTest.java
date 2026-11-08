package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.growth.model.policy.LevelTableFixture;
import com.wanted.moneybeetle.domain.growth.model.policy.UnlockRules;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/* comment.
*   담당 : DBA ② (성장)
*   RewardService 지급·사용·잔액 (이슈 #10 2차) - Repository·MemberService 를 Mock 으로 두고 서버·DB 없이 돈다.
*   이중 지급 방지, 잔액 부족, 규칙 없음 같은 실패 경우를 함께 확인한다 (SP-15, SP-18, NFR-06).
*  */
class RewardServiceGrantTest {

    private static final long MEMBER = 8L;

    private RewardRuleRepository ruleRepository;
    private CashHistoryRepository cashHistoryRepository;
    private ExpHistoryRepository expHistoryRepository;
    private MemberService memberService;
    private GrowthService growthService;
    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(RewardRuleRepository.class);
        cashHistoryRepository = mock(CashHistoryRepository.class);
        expHistoryRepository = mock(ExpHistoryRepository.class);
        memberService = mock(MemberService.class);
        growthService = mock(GrowthService.class);
        // 레벨 정책표는 이슈 #11 확정값(Lv.8 = 1,250, Lv.12 = 2,450)을 쓴다
        when(growthService.loadLevelTable()).thenReturn(LevelTableFixture.table());
        rewardService = new RewardService(ruleRepository, cashHistoryRepository, expHistoryRepository,
                memberService, growthService);
    }

    private void givenRule(String code, int cash, int exp) {
        when(ruleRepository.findByCodeAndActiveTrue(code))
                .thenReturn(Optional.of(RewardRule.create(code, "규칙", cash, exp, null)));
    }

    private void givenBalance(int level, int exp, long cash) {
        when(memberService.lockBalance(MEMBER)).thenReturn(MemberBalanceDTO.of(MEMBER, level, exp, cash));
    }

    /** 이미 기록돼 있는 이력 (이중 지급·이중 차감 확인이 찾아내는 쪽) */
    private static ExpHistory existingExp() {
        return ExpHistory.create(MEMBER, ExpHistory.SOURCE_COURSE_COMPLETE, 15L, null, 100, 100, 1);
    }

    private static CashHistory existingCash(String sourceType) {
        return CashHistory.earn(MEMBER, sourceType, 15L, null, 30_000, 30_000);
    }

    private static void assertErrorCode(Throwable e, GrowthErrorCode expected) {
        assertThat(e).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("학습 완료: 캐시와 경험치를 함께 지급하고 이력 두 줄을 남긴다")
    void grantCourseComplete() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 300, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 400, 35_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isAlreadyGranted()).isFalse();
        assertThat(result.getGrantedCash()).isEqualTo(30_000);
        assertThat(result.getGrantedExp()).isEqualTo(100);
        assertThat(result.getCashBalance()).isEqualTo(35_000);
        assertThat(result.getExpTotal()).isEqualTo(400);
        assertThat(result.isLevelUp()).isFalse();   // Lv.4 는 누적 450 부터라 아직 오르지 않는다
        assertThat(result.getLevelUpCash()).isZero();
        verify(memberService, never()).levelUp(anyLong(), anyInt(), anyLong());

        ArgumentCaptor<CashHistory> cash = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository).save(cash.capture());
        assertThat(cash.getValue().getHistoryType()).isEqualTo(CashHistory.EARN);
        assertThat(cash.getValue().getSourceType()).isEqualTo(CashHistory.SOURCE_COURSE_COMPLETE);
        assertThat(cash.getValue().getSourceId()).isEqualTo(15L);
        assertThat(cash.getValue().getAmount()).isEqualTo(30_000);
        assertThat(cash.getValue().getBalanceAfter()).isEqualTo(35_000);

        ArgumentCaptor<ExpHistory> exp = ArgumentCaptor.forClass(ExpHistory.class);
        verify(expHistoryRepository).save(exp.capture());
        assertThat(exp.getValue().getSourceType()).isEqualTo(ExpHistory.SOURCE_COURSE_COMPLETE);
        assertThat(exp.getValue().getAmount()).isEqualTo(100);
        assertThat(exp.getValue().getExpAfter()).isEqualTo(400);
        assertThat(exp.getValue().getLevelAfter()).isEqualTo(3);
    }

    @Test
    @DisplayName("경험치로 레벨이 한 칸 오르면 새 레벨로 올리고 레벨업 캐시를 주며 이력에 남긴다")
    void levelUpByOne() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 400, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 500, 35_000));
        when(memberService.levelUp(MEMBER, 4, 40_000L)).thenReturn(MemberBalanceDTO.of(MEMBER, 4, 500, 75_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isLevelUp()).isTrue();
        assertThat(result.getLevelFrom()).isEqualTo(3);
        assertThat(result.getLevelTo()).isEqualTo(4);
        assertThat(result.getGrantedCash()).isEqualTo(30_000);       // 규칙이 준 캐시
        assertThat(result.getLevelUpCash()).isEqualTo(40_000);       // 새 레벨 × 10,000
        assertThat(result.getCashBalance()).isEqualTo(75_000);       // 둘 다 더한 잔액
        assertThat(result.getUnlocks()).isEmpty();

        ArgumentCaptor<CashHistory> cash = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository, org.mockito.Mockito.times(2)).save(cash.capture());
        CashHistory levelCash = cash.getAllValues().get(1);
        assertThat(levelCash.getSourceType()).isEqualTo(CashHistory.SOURCE_LEVEL_UP);
        assertThat(levelCash.getSourceId()).isEqualTo(4L);           // 원인 ID = 오른 레벨
        assertThat(levelCash.getAmount()).isEqualTo(40_000);
        assertThat(levelCash.getBalanceAfter()).isEqualTo(75_000);

        ArgumentCaptor<ExpHistory> exp = ArgumentCaptor.forClass(ExpHistory.class);
        verify(expHistoryRepository).save(exp.capture());
        assertThat(exp.getValue().getLevelAfter()).isEqualTo(4);     // 이력의 레벨은 새 레벨
    }

    @Test
    @DisplayName("한 번에 여러 레벨이 오르면 레벨마다 레벨업 캐시를 모두 주고, 열린 기능도 빠짐없이 알려 준다 (SP-23)")
    void levelUpSeveralLevels() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 1_300);
        givenBalance(1, 0, 0);
        // 경험치 1,300 -> Lv.8 (모의투자 해금). 레벨업 캐시는 Lv.2~8 = 20,000 + ... + 80,000 = 350,000
        when(memberService.addReward(MEMBER, 0L, 1_300)).thenReturn(MemberBalanceDTO.of(MEMBER, 1, 1_300, 0));
        when(memberService.levelUp(MEMBER, 8, 350_000L)).thenReturn(MemberBalanceDTO.of(MEMBER, 8, 1_300, 350_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.getLevelFrom()).isEqualTo(1);
        assertThat(result.getLevelTo()).isEqualTo(8);
        assertThat(result.getLevelUpCash()).isEqualTo(350_000);
        assertThat(result.getUnlocks()).containsExactly(UnlockRules.TRADING);

        ArgumentCaptor<CashHistory> cash = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository, org.mockito.Mockito.times(7)).save(cash.capture());
        long running = 0;
        for (int i = 0; i < 7; i++) {
            int level = i + 2;
            running += level * 10_000L;
            CashHistory history = cash.getAllValues().get(i);
            assertThat(history.getSourceType()).isEqualTo(CashHistory.SOURCE_LEVEL_UP);
            assertThat(history.getSourceId()).isEqualTo((long) level);   // 레벨마다 한 줄
            assertThat(history.getBalanceAfter()).isEqualTo(running);
        }
    }

    @Test
    @DisplayName("Lv.12 에 오르면 모의 청약 해금도 알려 준다")
    void unlocksHousingAtLevel12() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 100);
        givenBalance(11, 2_400, 0);
        when(memberService.addReward(MEMBER, 0L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 11, 2_500, 0));
        when(memberService.levelUp(MEMBER, 12, 120_000L)).thenReturn(MemberBalanceDTO.of(MEMBER, 12, 2_500, 120_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.getUnlocks()).containsExactly(UnlockRules.HOUSING);
    }

    @Test
    @DisplayName("이미 높은 레벨(시드 관리자 Lv.20·경험치 6,000)은 경험치를 받아도 레벨이 내려가거나 바뀌지 않는다")
    void levelNeverGoesDown() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 100);
        givenBalance(20, 6_000, 0);
        when(memberService.addReward(MEMBER, 0L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 20, 6_100, 0));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isLevelUp()).isFalse();
        assertThat(result.getLevelTo()).isEqualTo(20);
        verify(memberService, never()).levelUp(anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("캐시만 주는 규칙(연속 출석)은 레벨 계산을 하지 않는다")
    void cashOnlyRuleSkipsLevelCheck() {
        givenRule(RewardRule.ATTENDANCE_STREAK, 10_000, 0);
        givenBalance(1, 0, 0);
        when(memberService.addReward(MEMBER, 10_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 1, 0, 10_000));

        rewardService.grant(MEMBER, RewardRule.ATTENDANCE_STREAK, 77L);

        verify(growthService, never()).loadLevelTable();
    }

    @Test
    @DisplayName("같은 강의로 다시 부르면 지급하지 않고 alreadyGranted 로 현재 값만 돌려준다 (이중 지급 방지)")
    void grantTwiceIsIgnored() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 500, 35_000);
        when(expHistoryRepository.findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, ExpHistory.SOURCE_COURSE_COMPLETE, 15L)).thenReturn(Optional.of(existingExp()));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isAlreadyGranted()).isTrue();
        assertThat(result.getGrantedCash()).isZero();
        assertThat(result.getCashBalance()).isEqualTo(35_000);
        verify(memberService, never()).addReward(anyLong(), anyLong(), anyInt());
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("규칙 값이 바뀌어 지금 경험치가 0이어도, 이미 경험치를 받은 강의면 캐시를 다시 주지 않는다")
    void alreadyGrantedEvenIfRuleChanged() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 0);   // 예전에는 경험치(100)만 주던 규칙이 캐시만 주도록 바뀜
        givenBalance(3, 500, 35_000);
        when(expHistoryRepository.findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, ExpHistory.SOURCE_COURSE_COMPLETE, 15L)).thenReturn(Optional.of(existingExp()));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isAlreadyGranted()).isTrue();
        verify(memberService, never()).addReward(anyLong(), anyLong(), anyInt());
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("반대로 예전에 캐시만 받았고 지금 규칙이 경험치만 주더라도 다시 주지 않는다")
    void alreadyGrantedEvenIfRuleChangedToExpOnly() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 100);
        givenBalance(3, 500, 35_000);
        when(cashHistoryRepository.findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, CashHistory.SOURCE_COURSE_COMPLETE, 15L)).thenReturn(Optional.of(existingCash(CashHistory.SOURCE_COURSE_COMPLETE)));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isAlreadyGranted()).isTrue();
        verify(memberService, never()).addReward(anyLong(), anyLong(), anyInt());
        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("지급 전에 회원 행을 잠근 뒤 이중 지급을 확인한다 (동시 요청 방지)")
    void locksMemberBeforeCheckingDuplicates() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(1, 0, 0);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 1, 100, 30_000));

        rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(memberService, expHistoryRepository);
        order.verify(memberService).lockBalance(MEMBER);
        order.verify(expHistoryRepository).findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, ExpHistory.SOURCE_COURSE_COMPLETE, 15L);
        order.verify(memberService).addReward(MEMBER, 30_000L, 100);
    }

    @Test
    @DisplayName("중복 확인은 스냅샷을 볼 수 있는 일반 exists 조회가 아니라 잠금 조회로만 한다 (지급·사용 모두)")
    void duplicateChecksUseLockingReads() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(1, 0, 50_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 1, 100, 80_000));
        when(memberService.deductCash(MEMBER, 1_000L)).thenReturn(MemberBalanceDTO.of(MEMBER, 1, 100, 79_000));

        rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);
        rewardService.spendCash(MEMBER, CashHistory.SOURCE_SEASON_JOIN, 1L, 1_000);

        verify(expHistoryRepository).findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, ExpHistory.SOURCE_COURSE_COMPLETE, 15L);
        verify(cashHistoryRepository).findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, CashHistory.SOURCE_COURSE_COMPLETE, 15L);
        verify(cashHistoryRepository).findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, CashHistory.SOURCE_SEASON_JOIN, 1L);
        verify(expHistoryRepository, never()).existsByMemberIdAndSourceTypeAndSourceId(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(cashHistoryRepository, never()).existsByMemberIdAndSourceTypeAndSourceId(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("연속 출석 보상은 캐시만 지급하고 출석 원인으로 기록한다")
    void grantAttendanceStreakCashOnly() {
        givenRule(RewardRule.ATTENDANCE_STREAK, 10_000, 0);
        givenBalance(1, 0, 0);
        when(memberService.addReward(MEMBER, 10_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 1, 0, 10_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.ATTENDANCE_STREAK, 77L);

        assertThat(result.getGrantedCash()).isEqualTo(10_000);
        assertThat(result.getGrantedExp()).isZero();
        ArgumentCaptor<CashHistory> cash = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository).save(cash.capture());
        assertThat(cash.getValue().getSourceType()).isEqualTo(CashHistory.SOURCE_ATTENDANCE);
        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("퀴즈 만점 보너스는 경험치만 지급하고 경험치 이력만 남긴다")
    void grantQuizPerfectExpOnly() {
        givenRule(RewardRule.QUIZ_PERFECT, 0, 20);
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 20)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 220, 1_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.QUIZ_PERFECT, 15L);

        assertThat(result.getGrantedExp()).isEqualTo(20);
        verify(expHistoryRepository).save(org.mockito.ArgumentMatchers.any(ExpHistory.class));
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("사용 중인 보상 규칙이 없으면 REWARD_RULE_NOT_FOUND 이고 아무것도 바꾸지 않는다")
    void ruleNotFound() {
        when(ruleRepository.findByCodeAndActiveTrue("COURSE_COMPLETE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rewardService.grant(MEMBER, "COURSE_COMPLETE", 15L))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.REWARD_RULE_NOT_FOUND));
        verify(memberService, never()).lockBalance(anyLong());
    }

    @Test
    @DisplayName("원인 종류가 정해지지 않은 규칙에 지급량이 있으면 REWARD_NOT_SUPPORTED (설정 오류)")
    void unsupportedRule() {
        givenRule(RewardRule.QUIZ_PERFECT, 5_000, 20);   // 퀴즈 만점은 캐시를 기록할 원인 종류가 없다

        assertThatThrownBy(() -> rewardService.grant(MEMBER, RewardRule.QUIZ_PERFECT, 15L))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.REWARD_NOT_SUPPORTED));
        verify(memberService, never()).addReward(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("지급할 것이 없는 규칙(캐시·경험치 0)은 이력 없이 지급량 0으로 끝난다")
    void ruleWithoutReward() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 0);
        givenBalance(1, 0, 0);

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isAlreadyGranted()).isFalse();
        assertThat(result.getGrantedCash()).isZero();
        verify(memberService, never()).addReward(anyLong(), anyLong(), anyInt());
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("회원 ID·원인 ID 가 없으면 거절한다")
    void grantRequiresIds() {
        assertThatThrownBy(() -> rewardService.grant(null, RewardRule.COURSE_COMPLETE, 15L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("캐시 사용: 잔액에서 차감하고 사용 이력(음수)을 남기며 사용 후 잔액을 돌려준다")
    void spendCash() {
        givenBalance(8, 1250, 50_000);
        when(memberService.deductCash(MEMBER, 20_000L)).thenReturn(MemberBalanceDTO.of(MEMBER, 8, 1250, 30_000));

        long balance = rewardService.spendCash(MEMBER, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT, 3L, 20_000);

        assertThat(balance).isEqualTo(30_000);
        ArgumentCaptor<CashHistory> history = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository).save(history.capture());
        assertThat(history.getValue().getHistoryType()).isEqualTo(CashHistory.USE);
        assertThat(history.getValue().getAmount()).isEqualTo(-20_000);
        assertThat(history.getValue().getBalanceAfter()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("잔액이 모자라면 NOT_ENOUGH_CASH 이고 차감도 이력도 없다 (잔액은 0 미만이 될 수 없다)")
    void notEnoughCash() {
        givenBalance(8, 1250, 10_000);

        assertThatThrownBy(() -> rewardService.spendCash(MEMBER, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT, 3L, 20_000))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.NOT_ENOUGH_CASH));
        verify(memberService, never()).deductCash(anyLong(), anyLong());
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("잔액과 똑같은 금액은 쓸 수 있다 (0이 되는 것은 허용)")
    void spendExactBalance() {
        givenBalance(8, 1250, 20_000);
        when(memberService.deductCash(MEMBER, 20_000L)).thenReturn(MemberBalanceDTO.of(MEMBER, 8, 1250, 0));

        assertThat(rewardService.spendCash(MEMBER, CashHistory.SOURCE_SEASON_JOIN, 1L, 20_000)).isZero();
    }

    @Test
    @DisplayName("같은 원인으로 이미 사용했으면 ALREADY_USED 로 거절해 이중 차감을 막는다")
    void alreadyUsed() {
        givenBalance(8, 1250, 50_000);
        when(cashHistoryRepository.findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT, 3L)).thenReturn(Optional.of(existingCash(CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT)));

        assertThatThrownBy(() -> rewardService.spendCash(MEMBER, CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT, 3L, 20_000))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.ALREADY_USED));
        verify(memberService, never()).deductCash(anyLong(), anyLong());
    }

    @Test
    @DisplayName("사용 금액이 0 이하면 INVALID_AMOUNT 이고 회원을 건드리지 않는다")
    void invalidAmount() {
        assertThatThrownBy(() -> rewardService.spendCash(MEMBER, CashHistory.SOURCE_SEASON_JOIN, 1L, 0))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.INVALID_AMOUNT));
        assertThatThrownBy(() -> rewardService.spendCash(MEMBER, CashHistory.SOURCE_SEASON_JOIN, 1L, -5))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.INVALID_AMOUNT));
        verify(memberService, never()).lockBalance(anyLong());
    }

    @Test
    @DisplayName("사용할 수 없는 원인 종류(출석 등)나 빠진 ID 는 호출한 쪽의 실수라 거절한다")
    void invalidSource() {
        assertThatThrownBy(() -> rewardService.spendCash(MEMBER, CashHistory.SOURCE_ATTENDANCE, 1L, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rewardService.spendCash(MEMBER, CashHistory.SOURCE_SEASON_JOIN, null, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잔액 조회는 회원의 현재 캐시를 돌려준다")
    void getBalance() {
        when(memberService.getBalance(MEMBER)).thenReturn(MemberBalanceDTO.of(MEMBER, 8, 1250, 12_000));

        assertThat(rewardService.getBalance(MEMBER)).isEqualTo(12_000);
    }
}
