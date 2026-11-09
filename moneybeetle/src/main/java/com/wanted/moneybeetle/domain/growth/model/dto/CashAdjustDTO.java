package com.wanted.moneybeetle.domain.growth.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   관리자 수동 지급·회수 요청 (API 110) { type: GIVE | TAKE, amount, reason }
*   - 형식(type 은 GIVE·TAKE, amount 는 있어야 함)은 여기서, 금액이 0 이하인지·사유가 비었는지는 CashAdjustService 가
*     INVALID_AMOUNT·REASON_REQUIRED 로 알려 준다 (명세의 에러 코드).
*  */
public record CashAdjustDTO(
        @NotNull @Pattern(regexp = "GIVE|TAKE") String type,
        @NotNull Long amount,
        String reason) {

    public boolean isGive() {
        return "GIVE".equals(type);
    }
}
