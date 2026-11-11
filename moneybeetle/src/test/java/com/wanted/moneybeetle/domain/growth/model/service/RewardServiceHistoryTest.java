package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.support.UnlockLevelFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.CashHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.ExpHistoryRepository;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.CashSource;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   RewardService 내역 조회 (API 22) - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class RewardServiceHistoryTest {

    private CashHistoryRepository cashHistoryRepository;
    private RewardService rewardService;
    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        cashHistoryRepository = mock(CashHistoryRepository.class);
        rewardService = new RewardService(mock(RewardRuleRepository.class), cashHistoryRepository,
                mock(ExpHistoryRepository.class), mock(MemberService.class), mock(GrowthService.class),
                mock(com.wanted.moneybeetle.domain.notification.model.service.NotificationService.class),
                UnlockLevelFixture.defaults());
    }

    @Test
    @DisplayName("type 이 없으면 전체 내역을 조회한다")
    void allHistories() {
        CashHistory earn = CashHistory.earn(1L, CashSource.ATTENDANCE, 1L, 1L, 1000, 1000);
        when(cashHistoryRepository.findByMemberIdOrderByCreatedAtDescCashHistoryIdDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(earn), pageable, 1));

        Page<CashHistoryDTO> result = rewardService.findCashHistories(1L, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("연속 출석 보상");
    }

    @Test
    @DisplayName("EARN 은 적립·관리자 지급, USE 는 사용·관리자 회수만 조회한다")
    void filterByType() {
        when(cashHistoryRepository.findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
                eq(1L), any(), eq(pageable))).thenReturn(Page.empty(pageable));

        rewardService.findCashHistories(1L, "EARN", pageable);
        verify(cashHistoryRepository).findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
                1L, List.of(CashHistory.EARN, CashHistory.ADMIN_GIVE), pageable);

        rewardService.findCashHistories(1L, "USE", pageable);
        verify(cashHistoryRepository).findByMemberIdAndHistoryTypeInOrderByCreatedAtDescCashHistoryIdDesc(
                1L, List.of(CashHistory.USE, CashHistory.ADMIN_TAKE), pageable);
    }

    @Test
    @DisplayName("알 수 없는 type 은 INVALID_HISTORY_TYPE 으로 거절한다")
    void unknownType() {
        assertThatThrownBy(() -> rewardService.findCashHistories(1L, "GIFT", pageable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GrowthErrorCode.INVALID_HISTORY_TYPE);
    }
}
