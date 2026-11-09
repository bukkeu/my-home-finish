package com.wanted.moneybeetle.domain.auth.model.dto;

/* comment.
*   담당 : DBA ② (회원·인증)
*   관리자 로그인 성공 응답 (API 83) { "memberId": 100, "role": "ADMIN" }
*  */
public record AdminLoginResultDTO(Long memberId, String role) {
}
