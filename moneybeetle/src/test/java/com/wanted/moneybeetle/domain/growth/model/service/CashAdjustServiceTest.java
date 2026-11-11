package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.CashAdjustDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CashAdjustServiceTest {

    private static final Long ADMIN = 1L;
    private static final Long MEMBER = 8L;

    private MemberService memberService;
    private CashHistoryRepository cashHistoryRepository;
    private AdminLogService adminLogService;
    private CashAdjustService service;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        cashHistoryRepository = mock(CashHistoryRepository.class);
        adminLogService = mock(AdminLogService.class);
        // 캐시를 바꾸는 일은 RewardService 가 한다. 실제 RewardService 에 저장소만 가짜로 넣어 지급·회수 흐름까지 함께 확인한다
        RewardService rewardService = new RewardService(
                mock(com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository.class),
                cashHistoryRepository,
                mock(com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository.class),
                memberService, mock(GrowthService.class),
                mock(com.wanted.moneybeetle.domain.notification.model.service.NotificationService.class));
        service = new CashAdjustService(rewardService, adminLogService);
        when(memberService.lockBalance(MEMBER)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 100, 12_400));
        when(cashHistoryRepository.save(any(CashHistory.class))).thenAnswer(invocation -> {
            CashHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "cashHistoryId", 130L);
            return saved;
        });
    }

    @Test
    @DisplayName("지급: 캐시를 더하고 ADMIN_GIVE 내역과 CASH_GIVE 작업 기록을 남긴다")
    void gives() {
        when(memberService.addReward(MEMBER, 2000L, 0)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 100, 14_400));

        var result = service.adjust(ADMIN, MEMBER, new CashAdjustDTO("GIVE", 2000L, " 출석 누락 보정 "));

        assertThat(result.cashHistoryId()).isEqualTo(130L);
        assertThat(result.balanceAfter()).isEqualTo(14_400L);
        ArgumentCaptor<CashHistory> history = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository).save(history.capture());
        assertThat(history.getValue().getHistoryType()).isEqualTo(CashHistory.ADMIN_GIVE);
        assertThat(history.getValue().getAmount()).isEqualTo(2000L);
        assertThat(history.getValue().getReason()).isEqualTo("출석 누락 보정");
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(adminLogService).record(eq(ADMIN), eq("CASH_GIVE"), eq("MEMBER"), eq(MEMBER), detail.capture());
        assertThat(detail.getValue()).contains("12400").contains("14400").contains("출석 누락 보정");
    }

    @Test
    @DisplayName("회수: 캐시를 빼고 ADMIN_TAKE 내역과 CASH_TAKE 작업 기록을 남긴다")
    void takes() {
        when(memberService.deductCash(MEMBER, 400L)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 100, 12_000));

        var result = service.adjust(ADMIN, MEMBER, new CashAdjustDTO("TAKE", 400L, "중복 지급 회수"));

        assertThat(result.balanceAfter()).isEqualTo(12_000L);
        ArgumentCaptor<CashHistory> history = ArgumentCaptor.forClass(CashHistory.class);
        verify(cashHistoryRepository).save(history.capture());
        assertThat(history.getValue().getHistoryType()).isEqualTo(CashHistory.ADMIN_TAKE);
        assertThat(history.getValue().getAmount()).isEqualTo(-400L);
        verify(adminLogService).record(eq(ADMIN), eq("CASH_TAKE"), eq("MEMBER"), eq(MEMBER), any());
    }

    @Test
    @DisplayName("회수 금액이 잔액보다 크면 NOT_ENOUGH_CASH 이고 아무것도 바꾸지 않는다")
    void takeMoreThanBalance() {
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER, new CashAdjustDTO("TAKE", 12_401L, "회수")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.NOT_ENOUGH_CASH));

        verify(memberService, never()).deductCash(anyLong(), anyLong());
        verify(cashHistoryRepository, never()).save(any());
        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("잔액과 같은 금액은 회수할 수 있다 (0 이 된다)")
    void takeExactBalance() {
        when(memberService.deductCash(MEMBER, 12_400L)).thenReturn(MemberBalanceDTO.of(MEMBER, 3, 100, 0));

        assertThat(service.adjust(ADMIN, MEMBER, new CashAdjustDTO("TAKE", 12_400L, "전액 회수")).balanceAfter())
                .isZero();
    }

    @Test
    @DisplayName("사유가 비었으면 REASON_REQUIRED, 금액이 0 이하이면 INVALID_AMOUNT 이고 회원 행도 잠그지 않는다")
    void rejectsBadInput() {
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER, new CashAdjustDTO("GIVE", 1000L, "   ")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.REASON_REQUIRED));
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER, new CashAdjustDTO("GIVE", 1000L, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.REASON_REQUIRED));
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER, new CashAdjustDTO("GIVE", 0L, "사유")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_AMOUNT));
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER, new CashAdjustDTO("TAKE", -5L, "사유")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_AMOUNT));

        verify(memberService, never()).lockBalance(anyLong());
        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("한 번에 지급·회수할 수 있는 최대 금액을 넘으면 INVALID_AMOUNT 이고 잔액이 넘치지 않도록 회원 행도 건드리지 않는다")
    void rejectsAmountAboveCap() {
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER,
                new CashAdjustDTO("GIVE", CashAdjustService.MAX_AMOUNT + 1, "사유")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_AMOUNT));
        assertThatThrownBy(() -> service.adjust(ADMIN, MEMBER, new CashAdjustDTO("GIVE", Long.MAX_VALUE, "사유")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_AMOUNT));

        verify(memberService, never()).lockBalance(anyLong());
    }

    @Test
    @DisplayName("사유는 DB 컬럼 길이(200자)까지만 받는다 - 입력 검사(@Size)가 붙어 있다")
    void reasonLengthMatchesColumn() throws NoSuchFieldException {
        var size = CashAdjustDTO.class.getDeclaredField("reason").getAnnotation(jakarta.validation.constraints.Size.class);

        assertThat(size).isNotNull();
        assertThat(size.max()).isEqualTo(CashAdjustDTO.REASON_MAX_LENGTH).isEqualTo(200);
    }
}
