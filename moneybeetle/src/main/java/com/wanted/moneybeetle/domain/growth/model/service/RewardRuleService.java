package com.wanted.moneybeetle.domain.growth.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.growth.model.dao.RewardRuleRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleListDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleUpdateDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   관리자 보상 규칙 관리 (이슈 #39, API 108·109, ADM-29)
*   - findRules : 꺼진 규칙까지 모두 돌려준다
*   - updateRule : 지급 캐시·경험치·사용 여부를 바꾸고, 값이 실제로 바뀌었을 때만 admin_log 에 변경 전·후를 남긴다 (DP-43).
*       기록은 같은 트랜잭션이라 수정이 되돌려지면 기록도 사라진다.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardRuleService {

    static final String ACTION_UPDATE = "REWARD_RULE_UPDATE";
    static final String TARGET_TYPE = "REWARD_RULE";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RewardRuleRepository rewardRuleRepository;
    private final AdminLogService adminLogService;

    public RewardRuleListDTO findRules() {
        return new RewardRuleListDTO(rewardRuleRepository.findAllByOrderByRewardRuleIdAsc().stream()
                .map(RewardRuleDTO::from)
                .toList());
    }

    /** 없는 규칙이면 NOT_FOUND(404) */
    @Transactional
    public void updateRule(Long adminId, Long ruleId, RewardRuleUpdateDTO request) {
        RewardRule rule = rewardRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Map<String, Object> before = snapshot(rule);

        rule.changeAmounts(request.cashAmount(), request.expAmount());
        if (request.isActive()) {
            rule.activate();
        } else {
            rule.deactivate();
        }

        Map<String, Object> after = snapshot(rule);
        if (before.equals(after)) {
            return;
        }
        adminLogService.record(adminId, ACTION_UPDATE, TARGET_TYPE, ruleId, toJson(before, after));
    }

    private static Map<String, Object> snapshot(RewardRule rule) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", rule.getCode());
        values.put("cashAmount", rule.getCashAmount());
        values.put("expAmount", rule.getExpAmount());
        values.put("isActive", rule.isActive());
        return values;
    }

    private static String toJson(Map<String, Object> before, Map<String, Object> after) {
        try {
            return JSON.writeValueAsString(Map.of("before", before, "after", after));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("작업 기록을 만들지 못했습니다.", e);
        }
    }
}
