package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleUpdateDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RewardRuleServiceTest {

    private RewardRuleRepository repository;
    private AdminLogService adminLogService;
    private RewardRuleService service;
    private RewardRule rule;

    @BeforeEach
    void setUp() {
        repository = mock(RewardRuleRepository.class);
        adminLogService = mock(AdminLogService.class);
        service = new RewardRuleService(repository, adminLogService);
        rule = RewardRule.create(RewardRule.COURSE_COMPLETE, "학습 완료", 3000, 100, null);
        ReflectionTestUtils.setField(rule, "rewardRuleId", 1L);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(rule));
    }

    @Test
    @DisplayName("꺼진 규칙까지 모두 돌려준다")
    void findsAllRulesIncludingInactive() {
        RewardRule off = RewardRule.create(RewardRule.QUIZ_PERFECT, "퀴즈 만점", 500, 50, null);
        off.deactivate();
        when(repository.findAllByOrderByRewardRuleIdAsc()).thenReturn(List.of(rule, off));

        var result = service.findRules();

        assertThat(result.rules()).hasSize(2);
        assertThat(result.rules().get(0).code()).isEqualTo("COURSE_COMPLETE");
        assertThat(result.rules().get(1).isActive()).isFalse();
    }

    @Test
    @DisplayName("금액을 바꾸면 변경 전·후를 admin_log 에 남긴다")
    void updatesAndLogsBeforeAfter() {
        service.updateRule(9L, 1L, new RewardRuleUpdateDTO(5000, 200, true));

        assertThat(rule.getCashAmount()).isEqualTo(5000);
        assertThat(rule.getExpAmount()).isEqualTo(200);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(adminLogService).record(eq(9L), eq("REWARD_RULE_UPDATE"), eq("REWARD_RULE"), eq(1L), detail.capture());
        assertThat(detail.getValue()).contains("\"before\"").contains("3000").contains("\"after\"").contains("5000");
    }

    @Test
    @DisplayName("사용 여부만 꺼도 바뀐 것이라 기록한다")
    void deactivationIsLogged() {
        service.updateRule(9L, 1L, new RewardRuleUpdateDTO(3000, 100, false));

        assertThat(rule.isActive()).isFalse();
        verify(adminLogService).record(eq(9L), anyString(), anyString(), eq(1L), any());
    }

    @Test
    @DisplayName("값이 그대로면 기록하지 않는다")
    void unchangedIsNotLogged() {
        service.updateRule(9L, 1L, new RewardRuleUpdateDTO(3000, 100, true));

        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }

    private RewardRule ruleWithId(String code, int cash, int exp, long id) {
        RewardRule r = RewardRule.create(code, code, cash, exp, null);
        ReflectionTestUtils.setField(r, "rewardRuleId", id);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        return r;
    }

    private void assertRejected(long id, RewardRuleUpdateDTO request) {
        assertThatThrownBy(() -> service.updateRule(9L, id, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REWARD_RULE_AMOUNTS));
        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#244 출석 경험치(ATTENDANCE_DAILY)를 쓰는 채로 캐시를 넣으면 거절하고 값을 바꾸지 않는다")
    void rejectsCashOnAttendanceDaily() {
        RewardRule daily = ruleWithId(RewardRule.ATTENDANCE_DAILY, 0, 10, 2L);

        assertRejected(2L, new RewardRuleUpdateDTO(1000, 20, true));
        assertThat(daily.getCashAmount()).isZero();
        assertThat(daily.getExpAmount()).isEqualTo(10);
    }

    @Test
    @DisplayName("#244 랭킹 보상(RANKING_RANK_1~3)을 쓰는 채로 경험치를 넣으면 거절한다")
    void rejectsExpOnRankingRules() {
        ruleWithId(RewardRule.RANKING_RANK_1, 50000, 0, 3L);
        ruleWithId(RewardRule.RANKING_RANK_3, 10000, 0, 4L);

        assertRejected(3L, new RewardRuleUpdateDTO(50000, 100, true));
        assertRejected(4L, new RewardRuleUpdateDTO(10000, 1, true));
    }

    @Test
    @DisplayName("#244 연속 출석(ATTENDANCE_STREAK)·퀴즈 만점(QUIZ_PERFECT)도 줄 수 없는 쪽 금액은 거절한다")
    void rejectsOtherUnsupportedSides() {
        ruleWithId(RewardRule.ATTENDANCE_STREAK, 10000, 0, 5L);
        ruleWithId(RewardRule.QUIZ_PERFECT, 0, 30, 6L);

        assertRejected(5L, new RewardRuleUpdateDTO(10000, 10, true));
        assertRejected(6L, new RewardRuleUpdateDTO(500, 30, true));
    }

    @Test
    @DisplayName("#244 그 규칙이 줄 수 있는 금액이면 그대로 저장한다")
    void acceptsSupportedAmounts() {
        RewardRule daily = ruleWithId(RewardRule.ATTENDANCE_DAILY, 0, 10, 2L);
        RewardRule rank = ruleWithId(RewardRule.RANKING_RANK_2, 30000, 0, 3L);

        service.updateRule(9L, 2L, new RewardRuleUpdateDTO(0, 20, true));
        service.updateRule(9L, 3L, new RewardRuleUpdateDTO(40000, 0, true));

        assertThat(daily.getExpAmount()).isEqualTo(20);
        assertThat(rank.getCashAmount()).isEqualTo(40000);
    }

    @Test
    @DisplayName("#244 끄는 요청은 줄 수 없는 금액이 있어도 저장한다 (지급하지 않으므로)")
    void deactivationIsAllowedWithUnsupportedAmounts() {
        RewardRule daily = ruleWithId(RewardRule.ATTENDANCE_DAILY, 0, 10, 2L);

        service.updateRule(9L, 2L, new RewardRuleUpdateDTO(1000, 10, false));

        assertThat(daily.isActive()).isFalse();
        assertThat(daily.getCashAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("없는 규칙이면 NOT_FOUND")
    void notFound() {
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(9L, 99L, new RewardRuleUpdateDTO(1, 1, true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verify(adminLogService, never()).record(any(), any(), any(), any(), any());
    }
}
