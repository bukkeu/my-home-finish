package com.wanted.moneybeetle.domain.trading.model.dto;

import java.time.LocalDateTime;

/* comment.
*   담당 : PM (#46)
*   랭킹 공개 결과 (API 139) : 공개 시각
*  */
public record RankingPublishedDTO(LocalDateTime publishedAt) {
}
