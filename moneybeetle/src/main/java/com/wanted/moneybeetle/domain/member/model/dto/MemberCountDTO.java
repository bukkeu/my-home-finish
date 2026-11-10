package com.wanted.moneybeetle.domain.member.model.dto;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 수 (관리자 대시보드 members, API 84) { total, withdrawn }
*   - total 은 탈퇴한 회원을 포함한 학습자(MEMBER) 수이고, withdrawn 은 그중 탈퇴한 수다.
*  */
public record MemberCountDTO(long total, long withdrawn) {
}
