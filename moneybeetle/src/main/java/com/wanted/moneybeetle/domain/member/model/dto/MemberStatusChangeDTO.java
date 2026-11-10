package com.wanted.moneybeetle.domain.member.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   API 102 계정 정지·해제 요청 { "status": "SUSPENDED", "reason": "부적절한 문의 반복" }
*   - status: ACTIVE 또는 SUSPENDED. 정지할 때 사유 필수(REASON_REQUIRED, 서비스가 검사)
*  */
public record MemberStatusChangeDTO(
        @NotBlank(message = "바꿀 상태를 골라 주세요") String status,
        @Size(max = 200, message = "정지 사유는 200자까지 쓸 수 있어요") String reason
) {
}
