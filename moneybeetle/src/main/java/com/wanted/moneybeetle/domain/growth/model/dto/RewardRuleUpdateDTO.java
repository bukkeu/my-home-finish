package com.wanted.moneybeetle.domain.growth.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   보상 규칙 수정 요청 (API 109) { cashAmount, expAmount, isActive }
*   - 입력값만으로 판단되는 규칙(필수, 0 이상)은 여기서 확인한다.
*  */
public record RewardRuleUpdateDTO(
        @NotNull @Min(0) Integer cashAmount,
        @NotNull @Min(0) Integer expAmount,
        @NotNull @JsonProperty("isActive") Boolean isActive) {
}
