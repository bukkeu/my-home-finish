package com.wanted.moneybeetle.domain.growth.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   보상 규칙 목록 (API 108 응답) { rules: [...] }
*  */
public record RewardRuleListDTO(List<RewardRuleDTO> rules) {
}
