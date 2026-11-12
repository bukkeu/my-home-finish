package com.wanted.moneybeetle.domain.trading.model.dto;

/* comment.
*   담당 : PM (모의투자)
*   회원 한 명의 랭킹 수상 한 줄 (#338) - 공개 프로필(API 18)의 awards 가 이 값을 그대로 옮겨 쓴다
*   rankingType : ASSET(순자산) / RETURN(수익률), rank : 1~3
*  */
public record RankingAwardDTO(String seasonName, String rankingType, int rank) {
}
