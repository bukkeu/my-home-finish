package com.wanted.moneybeetle.domain.growth.model.dto;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import java.time.LocalDateTime;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   캐시 내역 한 줄 (API 22 items)
*   title 은 원인 종류별 기본 문구다. "강의 완료 · 강의 제목"처럼 대상 이름을 붙이는 일은 course 도메인 연동 때 한다.
*  */
@Getter
public class CashHistoryDTO {

    private final Long cashHistoryId;
    private final String historyType;
    private final String title;
    private final long amount;
    private final long balanceAfter;
    private final LocalDateTime createdAt;

    private CashHistoryDTO(Long cashHistoryId, String historyType, String title,
                           long amount, long balanceAfter, LocalDateTime createdAt) {
        this.cashHistoryId = cashHistoryId;
        this.historyType = historyType;
        this.title = title;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public static CashHistoryDTO from(CashHistory history) {
        return new CashHistoryDTO(
                history.getCashHistoryId(),
                history.getHistoryType(),
                titleOf(history),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getCreatedAt());
    }

    private static String titleOf(CashHistory history) {
        return switch (history.getSourceType()) {
            case CashHistory.SOURCE_ATTENDANCE -> "연속 출석 보상";
            case CashHistory.SOURCE_COURSE_COMPLETE -> "강의 완료";
            case CashHistory.SOURCE_LEVEL_UP -> "레벨업 보상";
            case CashHistory.SOURCE_SEASON_JOIN -> "모의투자 시즌 참가";
            case CashHistory.SOURCE_SUBSCRIPTION_DEPOSIT -> "청약통장 납입";
            case CashHistory.SOURCE_ADMIN -> history.getReason() == null ? "관리자 조정" : "관리자 조정 · " + history.getReason();
            default -> history.getSourceType();
        };
    }
}
