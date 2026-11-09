package com.wanted.moneybeetle.domain.growth.model.dto;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   관리자 수동 지급·회수 결과 (API 110 응답) { cashHistoryId, balanceAfter }
*  */
public record CashAdjustResultDTO(Long cashHistoryId, long balanceAfter) {
}
