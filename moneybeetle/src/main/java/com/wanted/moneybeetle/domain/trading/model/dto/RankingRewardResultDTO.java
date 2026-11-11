package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (#46)
*   랭킹 보상 지급 결과 (API 140) : 이번에 새로 지급한 수상 수 (이미 지급한 수상은 세지 않는다)
*  */
public record RankingRewardResultDTO(int awarded) {
}
