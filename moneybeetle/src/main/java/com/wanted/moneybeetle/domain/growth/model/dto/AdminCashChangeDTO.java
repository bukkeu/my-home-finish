package com.wanted.moneybeetle.domain.growth.model.dto;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   관리자 수동 지급·회수가 끝난 결과 (RewardService.adjustByAdmin 의 반환값, 이슈 #39)
*   - 작업 기록(admin_log)에 변경 전·후 잔액을 남기려고 balanceBefore 도 함께 돌려준다.
*  */
public record AdminCashChangeDTO(long cashHistoryId, long balanceBefore, long balanceAfter) {
}
