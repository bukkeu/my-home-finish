package com.wanted.moneybeetle.domain.member.model.dto;

/* comment.
*   담당 : DBA ② (회원·인증)
*   설정 수정 응답 (API 14) { "memberId": 1, "nickname": "민지짱" }
*  */
public record MemberUpdateResultDTO(Long memberId, String nickname) {
}
