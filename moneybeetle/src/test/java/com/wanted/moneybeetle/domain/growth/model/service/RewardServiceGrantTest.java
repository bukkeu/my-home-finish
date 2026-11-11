package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.support.UnlockLevelFixture;
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
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
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
    private com.wanted.moneybeetle.domain.notification.model.service.NotificationService notificationService;
    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(RewardRuleRepository.class);
        cashHistoryRepository = mock(CashHistoryRepository.class);
        expHistoryRepository = mock(ExpHistoryRepository.class);
        memberService = mock(MemberService.class);
        growthService = mock(GrowthService.class);
        notificationService = mock(com.wanted.moneybeetle.domain.notification.model.service.NotificationService.class);
        // 레벨 정책표는 이슈 #11 확정값(Lv.8 = 1,250, Lv.12 = 2,450)을 쓴다
        when(growthService.loadLevelTable()).thenReturn(LevelTableFixture.table());
        rewardService = new RewardService(ruleRepository, cashHistoryRepository, expHistoryRepository,
                memberService, growthService, notificationService, UnlockLevelFixture.defaults());
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
        verify(memberService, never()).levelUp(anyLong(), anyInt());

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
        when(memberService.levelUp(MEMBER, 4)).thenReturn(MemberBalanceDTO.of(MEMBER, 4, 500, 35_000));
        when(memberService.addReward(MEMBER, 40_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 4, 500, 75_000));

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
        when(memberService.levelUp(MEMBER, 8)).thenReturn(MemberBalanceDTO.of(MEMBER, 8, 1_300, 0));
        when(memberService.addReward(MEMBER, 350_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 8, 1_300, 350_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.getLevelFrom()).isEqualTo(1);
        assertThat(result.getLevelTo()).isEqualTo(8);
        assertThat(result.getLevelUpCash()).isEqualTo(350_000);
        assertThat(result.getUnlocks()).containsExactly(UnlockRules.TRADING);
        verify(notificationService).send(MEMBER, com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.UNLOCK, "모의투자가 열렸어요", "/user/demo-trading");

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
        when(memberService.levelUp(MEMBER, 12)).thenReturn(MemberBalanceDTO.of(MEMBER, 12, 2_500, 0));
        when(memberService.addReward(MEMBER, 120_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 12, 2_500, 120_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.getUnlocks()).containsExactly(UnlockRules.HOUSING);
        verify(notificationService).send(MEMBER, com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.UNLOCK, "모의 청약이 열렸어요", "/user/demo-housing-subscription");
        verify(notificationService, org.mockito.Mockito.times(1))
                .send(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.class),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("경험치만 늘고 레벨이 오르지 않으면(Lv.2 그대로) 해금 알림을 보내지 않는다")
    void noUnlockNotificationWithoutLevelUp() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 100);
        givenBalance(2, 150, 0);   // Lv.3 은 누적 300 부터라 250 은 아직 Lv.2
        when(memberService.addReward(MEMBER, 0L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 250, 0));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isLevelUp()).isFalse();
        verify(memberService, never()).levelUp(anyLong(), anyInt());
        org.mockito.Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("레벨이 올라도(Lv.2 -> Lv.3) 새로 열린 기능이 없으면 해금 알림을 보내지 않는다")
    void noUnlockNotificationWhenLevelUpOpensNothing() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 100);
        givenBalance(2, 250, 0);   // 250 + 100 = 350 -> Lv.3 (누적 300 부터). 모의투자는 Lv.8, 모의 청약은 Lv.12 에 열린다
        when(memberService.addReward(MEMBER, 0L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 350, 0));
        when(memberService.levelUp(MEMBER, 3)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 350, 0));
        when(memberService.addReward(MEMBER, 30_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 350, 30_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.isLevelUp()).isTrue();
        assertThat(result.getLevelTo()).isEqualTo(3);
        assertThat(result.getUnlocks()).isEmpty();
        verify(memberService).levelUp(MEMBER, 3);
        org.mockito.Mockito.verifyNoInteractions(notificationService);
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
        verify(memberService, never()).levelUp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("레벨업 캐시도 규칙 캐시와 같은 addReward 경로로 주고, 레벨업 이력의 시작 잔액은 (최종 잔액 - 레벨업 캐시)다")
    void levelUpCashUsesAddRewardAndHistoryStartsFromBalanceBefore() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 400, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 500, 35_000));
        when(memberService.levelUp(MEMBER, 4)).thenReturn(MemberBalanceDTO.of(MEMBER, 4, 500, 35_000));
        // 다른 곳에서 잔액이 더 변해 있어도(여기서는 35,000 이 아닌 값) 이력은 addReward 가 돌려준 최종 잔액에서 거꾸로 센다
        when(memberService.addReward(MEMBER, 40_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 4, 500, 80_000));

        RewardResultDTO result = rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L);

        assertThat(result.getCashBalance()).isEqualTo(80_000);
        ArgumentCaptor<CashHistory> cash = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository, org.mockito.Mockito.times(2)).save(cash.capture());
        assertThat(cash.getAllValues().get(1).getBalanceAfter()).isEqualTo(80_000);   // 40,000 + (80,000 - 40,000)
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(memberService);
        order.verify(memberService).addReward(MEMBER, 30_000L, 100);
        order.verify(memberService).levelUp(MEMBER, 4);
        order.verify(memberService).addReward(MEMBER, 40_000L, 0);
    }

    @Test
    @DisplayName("레벨 정책표를 읽지 못하면 LEVEL_POLICY_INVALID 로 멈추고 경험치 이력을 남기지 않는다")
    void levelTableLoadFailureStopsBeforeExpHistory() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 400, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 500, 35_000));
        when(growthService.loadLevelTable()).thenThrow(new BusinessException(GrowthErrorCode.LEVEL_POLICY_INVALID));

        assertThatThrownBy(() -> rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.LEVEL_POLICY_INVALID));

        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(memberService, never()).levelUp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("정책표 행 수가 최대 레벨(20)과 다르면 LEVEL_POLICY_INVALID 로 멈춘다")
    void levelTableWithWrongSizeIsInvalid() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 400, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 500, 35_000));
        // 19줄짜리 표: LevelTable 자체는 유효하지만 최대 레벨(Member.MAX_LEVEL = 20)과 맞지 않는다
        when(growthService.loadLevelTable()).thenReturn(
                new LevelTable(
                        LevelTableFixture.policies().subList(0, 19)));

        assertThatThrownBy(() -> rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.LEVEL_POLICY_INVALID));

        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(memberService, never()).levelUp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("레벨 올리기가 실패하면 예외를 그대로 던지고 레벨업 캐시·경험치 이력을 남기지 않는다")
    void levelUpFailureStopsBeforeExpHistory() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 400, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 500, 35_000));
        when(memberService.levelUp(MEMBER, 4)).thenThrow(new IllegalArgumentException("새 레벨은 현재 레벨보다 높아야 합니다."));

        assertThatThrownBy(() -> rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(memberService, never()).addReward(MEMBER, 40_000L, 0);
        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("레벨업 캐시를 주다 실패해도 경험치 이력을 남기지 않는다")
    void levelUpCashFailureStopsBeforeExpHistory() {
        givenRule(RewardRule.COURSE_COMPLETE, 30_000, 100);
        givenBalance(3, 400, 5_000);
        when(memberService.addReward(MEMBER, 30_000L, 100)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 500, 35_000));
        when(memberService.levelUp(MEMBER, 4)).thenReturn(MemberBalanceDTO.of(MEMBER, 4, 500, 35_000));
        when(memberService.addReward(MEMBER, 40_000L, 0)).thenThrow(new IllegalStateException("캐시 지급 실패"));

        assertThatThrownBy(() -> rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 15L))
                .isInstanceOf(IllegalStateException.class);

        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 되돌림(롤백)은 Mock 으로 직접 볼 수 없다. 대신 되돌려지는 구조인지를 확인한다:
     * grant 는 읽기 전용이 아닌 @Transactional 이라 하나의 쓰기 트랜잭션이고, 위에서 던지는 예외(BusinessException,
     * IllegalArgumentException)는 모두 RuntimeException 이라 기본 규칙대로 그 트랜잭션을 되돌린다.
     * 회원 잠금·캐시·경험치·이력은 모두 이 한 메서드 안에서 처리되고, MemberService·GrowthService 의 @Transactional 은
     * 같은 트랜잭션에 참여한다. 실제 롤백은 DB 를 띄운 통합 테스트에서 확인해야 한다.
     */
    @Test
    @DisplayName("grant 는 하나의 쓰기 트랜잭션이고, 실패 예외는 RuntimeException 이라 기본 규칙으로 되돌려진다")
    void grantIsOneWriteTransaction() throws NoSuchMethodException {
        var grant = RewardService.class.getMethod("grant", Long.class, String.class, Long.class);
        var tx = grant.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(tx).as("grant 에 @Transactional 이 있어야 한다").isNotNull();
        assertThat(tx.readOnly()).isFalse();
        assertThat(tx.rollbackFor()).isEmpty();
        assertThat(tx.noRollbackFor()).isEmpty();
        assertThat(RuntimeException.class).isAssignableFrom(BusinessException.class);
        // 호출되는 서비스는 참여(REQUIRED)라서 별도 트랜잭션을 열지 않는다
        assertThat(org.springframework.transaction.annotation.Propagation.REQUIRED).isEqualTo(tx.propagation());
        var memberLevelUp = MemberService.class.getMethod("levelUp", Long.class, int.class);
        assertThat(memberLevelUp.getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                .propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
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
    @DisplayName("출석 경험치: 규칙 기본값에 배수를 곱해 경험치만 지급하고, 이력 원인은 ATTENDANCE (#205)")
    void grantAttendanceDailyWithMultiplier() {
        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 10);
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 30)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 230, 1_000));

        RewardResultDTO result = rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 3).orElseThrow();

        assertThat(result.getGrantedExp()).isEqualTo(30);
        assertThat(result.getGrantedCash()).isZero();
        ArgumentCaptor<ExpHistory> exp = ArgumentCaptor.forClass(ExpHistory.class);
        verify(expHistoryRepository).save(exp.capture());
        assertThat(exp.getValue().getSourceType()).isEqualTo(ExpHistory.SOURCE_ATTENDANCE);
        assertThat(exp.getValue().getSourceId()).isEqualTo(500L);
        assertThat(exp.getValue().getAmount()).isEqualTo(30);
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("배수가 1 보다 작으면 거절하고 아무것도 바꾸지 않는다")
    void multiplierMustBePositive() {
        assertThatThrownBy(() -> rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(memberService, never()).lockBalance(anyLong());
    }

    @Test
    @DisplayName("지급할 규칙이 없으면(꺼짐·없음) 예외 없이 빈 결과를 돌려주고 회원 행도 잠그지 않는다")
    void multiplierGrantSkipsWhenRuleMissing() {
        when(ruleRepository.findByCodeAndActiveTrue(RewardRule.ATTENDANCE_DAILY)).thenReturn(Optional.empty());

        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 2)).isEmpty();

        verify(memberService, never()).lockBalance(anyLong());
        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("경험치 기본값이 0 이면 빈 결과를 돌려주고 아무것도 바꾸지 않는다 (규칙은 한 번만 읽는다)")
    void multiplierGrantSkipsWhenExpIsZero() {
        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 0);

        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 3)).isEmpty();

        verify(ruleRepository, org.mockito.Mockito.times(1)).findByCodeAndActiveTrue(RewardRule.ATTENDANCE_DAILY);
        verify(memberService, never()).lockBalance(anyLong());
    }

    @Test
    @DisplayName("규칙은 한 번만 읽는다 (지급 중에 다시 조회하지 않는다)")
    void multiplierGrantReadsRuleOnce() {
        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 10);
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 20)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 220, 1_000));

        rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 2);

        verify(ruleRepository, org.mockito.Mockito.times(1)).findByCodeAndActiveTrue(RewardRule.ATTENDANCE_DAILY);
    }

    @Test
    @DisplayName("출석 경험치는 하루 50 XP 를 넘지 않는다: 기본값 10 × 5 = 50 은 그대로, 기본값을 20 으로 바꿔도 ×5 는 50 으로 자른다 (SP-22)")
    void attendanceExpIsCappedAtFifty() {
        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 10);
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 50)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 250, 1_000));
        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 5).orElseThrow().getGrantedExp())
                .isEqualTo(50);

        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 20);
        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 501L, 5).orElseThrow().getGrantedExp())
                .isEqualTo(50);   // 20 × 5 = 100 → 50
    }

    @Test
    @DisplayName("상한 아래의 값은 자르지 않는다 (기본값 20 × 2 = 40)")
    void attendanceExpBelowCapIsKept() {
        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 20);
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 40)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 240, 1_000));

        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 2).orElseThrow().getGrantedExp())
                .isEqualTo(40);
    }

    @Test
    @DisplayName("기본값이 아주 커도 곱셈이 넘치지 않고(long 계산) 상한 50 으로 자른다")
    void attendanceExpDoesNotOverflow() {
        givenRule(RewardRule.ATTENDANCE_DAILY, 0, 2_000_000_000);   // 2,000,000,000 × 5 는 int 범위를 넘는다
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 50)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 250, 1_000));

        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.ATTENDANCE_DAILY, 500L, 5).orElseThrow().getGrantedExp())
                .isEqualTo(50);
    }

    @Test
    @DisplayName("상한은 출석 경험치에만 적용한다: 다른 규칙(여기서는 경험치 40)은 배수를 곱해도 50 으로 자르지 않고, 배수 없는 기존 grant 도 그대로다")
    void capAppliesOnlyToAttendance() {
        givenRule(RewardRule.COURSE_COMPLETE, 0, 40);
        givenBalance(2, 200, 1_000);
        when(memberService.addReward(MEMBER, 0L, 80)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 280, 1_000));
        assertThat(rewardService.grantWithExpMultiplier(MEMBER, RewardRule.COURSE_COMPLETE, 15L, 2).orElseThrow().getGrantedExp())
                .isEqualTo(80);   // 40 × 2 = 80 은 출석 상한(50)보다 크지만 다른 규칙이라 자르지 않는다

        when(memberService.addReward(MEMBER, 0L, 40)).thenReturn(MemberBalanceDTO.of(MEMBER, 2, 240, 1_000));
        assertThat(rewardService.grant(MEMBER, RewardRule.COURSE_COMPLETE, 16L).getGrantedExp()).isEqualTo(40);
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

    // ---- 시즌 랭킹 보상 (이슈 #10, SP-64) ----

    @Test
    @DisplayName("랭킹 1·2·3위 보상은 순위별 규칙(50,000 / 30,000 / 10,000)의 캐시만 지급하고 RANKING 원인·수상 기록 ID 로 남긴다")
    void grantRankingRewardByRank() {
        givenBalance(5, 900, 100_000);
        long[][] expected = {{1, 50_000, 71L}, {2, 30_000, 72L}, {3, 10_000, 73L}};
        for (long[] row : expected) {
            int rank = (int) row[0];
            long cash = row[1];
            long awardId = row[2];
            givenRule(RewardRule.rankingRuleCode(rank), (int) cash, 0);
            when(memberService.addReward(MEMBER, cash, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 5, 900, 100_000 + cash));

            RewardResultDTO result = rewardService.grantRankingReward(MEMBER, rank, awardId);

            assertThat(result.getGrantedCash()).isEqualTo(cash);
            assertThat(result.getGrantedExp()).isZero();
            assertThat(result.isAlreadyGranted()).isFalse();
        }

        ArgumentCaptor<CashHistory> history = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository, org.mockito.Mockito.times(3)).save(history.capture());
        assertThat(history.getAllValues()).extracting(CashHistory::getSourceType).containsOnly(CashHistory.SOURCE_RANKING);
        assertThat(history.getAllValues()).extracting(CashHistory::getSourceId).containsExactly(71L, 72L, 73L);
        assertThat(history.getAllValues()).extracting(CashHistory::getAmount).containsExactly(50_000L, 30_000L, 10_000L);
        verify(expHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("같은 수상 기록으로 다시 지급을 부르면 한 번만 지급된다 (이중 지급 방지)")
    void rankingRewardTwiceIsIgnored() {
        givenRule(RewardRule.RANKING_RANK_1, 50_000, 0);
        givenBalance(5, 900, 150_000);
        when(cashHistoryRepository.findWithLockByMemberIdAndSourceTypeAndSourceId(
                MEMBER, CashHistory.SOURCE_RANKING, 71L))
                .thenReturn(Optional.of(CashHistory.earn(MEMBER, CashHistory.SOURCE_RANKING, 71L, 1L, 50_000, 150_000)));

        RewardResultDTO result = rewardService.grantRankingReward(MEMBER, 1, 71L);

        assertThat(result.isAlreadyGranted()).isTrue();
        verify(memberService, never()).addReward(anyLong(), anyLong(), anyInt());
        verify(cashHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("순자산·수익률 랭킹에 모두 들면 수상 기록이 둘이라 두 번 받는다 (같은 순위여도 원인 ID 가 다르다)")
    void bothRankingsPayTwice() {
        givenRule(RewardRule.RANKING_RANK_1, 50_000, 0);
        givenBalance(5, 900, 0);
        when(memberService.addReward(MEMBER, 50_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 5, 900, 50_000));

        RewardResultDTO asset = rewardService.grantRankingReward(MEMBER, 1, 81L);    // 순자산 랭킹 수상
        RewardResultDTO returnRate = rewardService.grantRankingReward(MEMBER, 1, 82L);   // 수익률 랭킹 수상

        assertThat(asset.getGrantedCash()).isEqualTo(50_000);
        assertThat(returnRate.getGrantedCash()).isEqualTo(50_000);
        ArgumentCaptor<CashHistory> history = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(CashHistory::getSourceId).containsExactly(81L, 82L);
    }

    @Test
    @DisplayName("랭킹 보상 규칙이 없거나 꺼져 있으면 REWARD_RULE_NOT_FOUND 이고 아무것도 바꾸지 않는다")
    void rankingRuleMissing() {
        when(ruleRepository.findByCodeAndActiveTrue(RewardRule.RANKING_RANK_2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rewardService.grantRankingReward(MEMBER, 2, 72L))
                .satisfies(e -> assertErrorCode(e, GrowthErrorCode.REWARD_RULE_NOT_FOUND));
        verify(memberService, never()).lockBalance(anyLong());
    }

    @Test
    @DisplayName("관리자가 랭킹 보상 금액을 바꾸면 바뀐 금액으로 지급한다 (금액은 규칙에서 읽는다)")
    void rankingAmountComesFromRule() {
        givenRule(RewardRule.RANKING_RANK_3, 20_000, 0);   // 기본값 10,000 을 관리자가 20,000 으로 바꿈
        givenBalance(5, 900, 0);
        when(memberService.addReward(MEMBER, 20_000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 5, 900, 20_000));

        assertThat(rewardService.grantRankingReward(MEMBER, 3, 73L).getGrantedCash()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("1~3위가 아닌 순위는 거절하고 아무것도 조회하지 않는다")
    void rankOutOfRange() {
        for (int rank : new int[] {0, 4, -1}) {
            assertThatThrownBy(() -> rewardService.grantRankingReward(MEMBER, rank, 70L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(ruleRepository, never()).findByCodeAndActiveTrue(org.mockito.ArgumentMatchers.anyString());
        verify(memberService, never()).lockBalance(anyLong());
    }

    // ===== #266 해금 알림은 운영 설정의 해금 레벨에서만 =====

    private RewardService rewardServiceWith(Integer tradingLevel, Integer housingLevel) {
        return new RewardService(ruleRepository, cashHistoryRepository, expHistoryRepository, memberService,
                growthService, notificationService, UnlockLevelFixture.of(tradingLevel, housingLevel));
    }

    /** Lv.fromLevel → Lv.toLevel 로 오르는 지급을 흉내 낸다 (경험치는 레벨표의 필요 경험치에 맞춘다) */
    private void givenLevelUp(int fromLevel, int toLevel) {
        int fromExp = LevelTableFixture.policies().get(fromLevel - 1).getRequiredExp();
        int toExp = LevelTableFixture.policies().get(toLevel - 1).getRequiredExp();
        int gained = toExp - fromExp;
        givenRule(RewardRule.COURSE_COMPLETE, 0, gained);
        givenBalance(fromLevel, fromExp, 0);
        when(memberService.addReward(MEMBER, 0L, gained)).thenReturn(MemberBalanceDTO.of(MEMBER, fromLevel, toExp, 0));
        when(memberService.levelUp(MEMBER, toLevel)).thenReturn(MemberBalanceDTO.of(MEMBER, toLevel, toExp, 0));
        when(memberService.addReward(org.mockito.ArgumentMatchers.eq(MEMBER),
                org.mockito.ArgumentMatchers.longThat(cash -> cash > 0), org.mockito.ArgumentMatchers.eq(0)))
                .thenAnswer(invocation -> MemberBalanceDTO.of(MEMBER, toLevel, toExp, invocation.<Long>getArgument(1)));
    }

    @Test
    @DisplayName("청약 설정이 15 이면 Lv.12 에 올라도 청약 알림이 가지 않고, Lv.15 에 오를 때 간다")
    void housingNotificationAtSettingLevel() {
        RewardService service = rewardServiceWith(null, 15);
        givenLevelUp(11, 12);

        RewardResultDTO atTwelve = service.grant(MEMBER, RewardRule.COURSE_COMPLETE, 20L);

        assertThat(atTwelve.getUnlocks()).isEmpty();
        verify(notificationService, never()).send(anyLong(), org.mockito.ArgumentMatchers.any(com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        givenLevelUp(14, 15);
        RewardResultDTO atFifteen = service.grant(MEMBER, RewardRule.COURSE_COMPLETE, 21L);

        assertThat(atFifteen.getUnlocks()).containsExactly(UnlockRules.HOUSING);
        verify(notificationService).send(MEMBER, com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.UNLOCK, "모의 청약이 열렸어요", "/user/demo-housing-subscription");
    }

    @Test
    @DisplayName("모의투자 설정이 6 이면 Lv.6 에서 알림이 가고, 한 번에 Lv.2 → Lv.12 로 올라도 알림은 기능마다 한 번이다")
    void tradingNotificationAtSettingLevelAndOncePerFeature() {
        RewardService service = rewardServiceWith(6, null);
        givenLevelUp(5, 6);

        assertThat(service.grant(MEMBER, RewardRule.COURSE_COMPLETE, 30L).getUnlocks())
                .containsExactly(UnlockRules.TRADING);
        verify(notificationService).send(MEMBER, com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.UNLOCK, "모의투자가 열렸어요", "/user/demo-trading");

        org.mockito.Mockito.clearInvocations(notificationService);
        givenLevelUp(2, 12);

        assertThat(service.grant(MEMBER, RewardRule.COURSE_COMPLETE, 31L).getUnlocks())
                .containsExactly(UnlockRules.TRADING, UnlockRules.HOUSING);
        verify(notificationService, org.mockito.Mockito.times(1))
                .send(MEMBER, com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.UNLOCK, "모의투자가 열렸어요", "/user/demo-trading");
        verify(notificationService, org.mockito.Mockito.times(1))
                .send(MEMBER, com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.UNLOCK, "모의 청약이 열렸어요", "/user/demo-housing-subscription");
    }

    @Test
    @DisplayName("이미 해금 레벨을 지난 회원이 더 올라도 알림이 다시 가지 않는다 (청약 8, 모의투자 10)")
    void noRepeatAfterUnlock() {
        RewardService service = rewardServiceWith(10, 8);
        givenLevelUp(8, 9);

        assertThat(service.grant(MEMBER, RewardRule.COURSE_COMPLETE, 40L).getUnlocks()).isEmpty();
        verify(notificationService, never()).send(anyLong(), org.mockito.ArgumentMatchers.any(com.wanted.moneybeetle.domain.notification.model.entity.NotificationType.class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
