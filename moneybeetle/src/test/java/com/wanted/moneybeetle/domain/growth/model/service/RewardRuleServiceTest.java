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
