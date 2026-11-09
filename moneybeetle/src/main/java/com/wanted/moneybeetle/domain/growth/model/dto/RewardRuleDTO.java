package com.wanted.moneybeetle.domain.growth.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   보상 규칙 한 건 (API 108 응답) { rewardRuleId, code, name, cashAmount, expAmount, isActive }
*  */
public record RewardRuleDTO(Long rewardRuleId, String code, String name, int cashAmount, int expAmount,
                            @JsonProperty("isActive") boolean isActive) {

    public static RewardRuleDTO from(RewardRule rule) {
        return new RewardRuleDTO(rule.getRewardRuleId(), rule.getCode(), rule.getName(),
                rule.getCashAmount(), rule.getExpAmount(), rule.isActive());
    }
}
