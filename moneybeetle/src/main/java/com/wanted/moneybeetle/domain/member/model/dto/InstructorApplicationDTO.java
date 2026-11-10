package com.wanted.moneybeetle.domain.member.model.dto;

import java.time.LocalDateTime;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   API 104 강사 가입 신청 한 줄
*   { "memberId": 31, "displayName": "박서연", "field": "청약, ETF", "email": "teacher@moneybeetle.kr", "createdAt": "2026-11-04T10:00:00" }
*   - createdAt: 강사 가입(신청)한 시각 = 회원 가입 시각
*  */
public record InstructorApplicationDTO(Long memberId, String displayName, String field, String email,
                                       String approvalStatus, LocalDateTime createdAt) {
}
