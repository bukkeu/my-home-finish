package com.wanted.moneybeetle.domain.member.model.dto;

import jakarta.validation.constraints.NotBlank;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   API 103 강사 권한 부여·회수 요청 { "role": "INSTRUCTOR" } (MEMBER ↔ INSTRUCTOR)
*  */
public record MemberRoleChangeDTO(@NotBlank(message = "바꿀 권한을 골라 주세요") String role) {
}
