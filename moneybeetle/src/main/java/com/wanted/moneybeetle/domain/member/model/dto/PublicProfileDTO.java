package com.wanted.moneybeetle.domain.member.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (회원·인증)
*   다른 회원 공개 프로필 응답 (API 18) - 닉네임, 프로필 이미지, 칭호, 레벨, 랭킹 수상 내역만 담는다.
*   이메일·생년월일·성별·캐시 같은 개인정보는 필드 자체를 두지 않는다 (DP-03).
*   awards 는 모의투자 도메인(trading)의 RankingService.findAwards 로 채운다 (#338). 최근 시즌부터, 수상이 없으면 빈 목록이다.
*  */
public record PublicProfileDTO(
        Long memberId,
        String nickname,
        String profileImageUrl,
        int level,
        String title,
        List<Award> awards) {

    public record Award(String seasonName, String rankingType, int rank) {
    }
}
