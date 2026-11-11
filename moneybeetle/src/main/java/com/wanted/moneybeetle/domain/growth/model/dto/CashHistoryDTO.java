package com.wanted.moneybeetle.domain.growth.model.dto;

import com.wanted.moneybeetle.domain.growth.model.entity.CashHistory;
import com.wanted.moneybeetle.domain.growth.model.entity.CashSource;
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

    /** 원인 종류의 기본 문구(CashSource.label). 관리자 조정만 사유가 있으면 뒤에 붙인다 */
    private static String titleOf(CashHistory history) {
        CashSource source = history.getSourceType();
        if (source == CashSource.ADMIN && history.getReason() != null) {
            return source.getLabel() + " · " + history.getReason();
        }
        return source.getLabel();
    }
}
