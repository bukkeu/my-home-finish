package com.wanted.moneybeetle.domain.growth.model.dto;

import com.wanted.moneybeetle.domain.growth.model.entity.ExpHistory;
import java.time.LocalDateTime;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (성장)
*   경험치 내역 한 줄 (API 23 items)
*   title(강의 제목)은 course 도메인 연동 때 붙인다. 지금은 원인 종류별 기본 문구다.
*  */
@Getter
public class ExpHistoryDTO {

    private final Long expHistoryId;
    private final String sourceType;
    private final String title;
    private final int amount;
    private final int levelAfter;
    private final LocalDateTime createdAt;

    private ExpHistoryDTO(Long expHistoryId, String sourceType, String title,
                          int amount, int levelAfter, LocalDateTime createdAt) {
        this.expHistoryId = expHistoryId;
        this.sourceType = sourceType;
        this.title = title;
        this.amount = amount;
        this.levelAfter = levelAfter;
        this.createdAt = createdAt;
    }

    public static ExpHistoryDTO from(ExpHistory history) {
        return new ExpHistoryDTO(
                history.getExpHistoryId(),
                history.getSourceType(),
                titleOf(history.getSourceType()),
                history.getAmount(),
                history.getLevelAfter(),
                history.getCreatedAt());
    }

    private static String titleOf(String sourceType) {
        return switch (sourceType) {
            case ExpHistory.SOURCE_COURSE_COMPLETE -> "강의 학습 완료";
            case ExpHistory.SOURCE_QUIZ_PERFECT -> "퀴즈 만점 보너스";
            default -> sourceType;
        };
    }
}
