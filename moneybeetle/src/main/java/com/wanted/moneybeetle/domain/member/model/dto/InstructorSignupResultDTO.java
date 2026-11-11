package com.wanted.moneybeetle.domain.member.model.dto;

import lombok.Getter;

/* comment.
*   담당 : DBA ① 신아림 (#30, API 12 강사 가입 신청)
*   강사 가입 신청 응답 201 Created { "memberId": 31, "approvalStatus": "PENDING" }
*  */
@Getter
public class InstructorSignupResultDTO {

    private final Long memberId;
    private final String approvalStatus;

    private InstructorSignupResultDTO(Long memberId, String approvalStatus) {
        this.memberId = memberId;
        this.approvalStatus = approvalStatus;
    }

    public static InstructorSignupResultDTO of(Long memberId, String approvalStatus) {
        return new InstructorSignupResultDTO(memberId, approvalStatus);
    }
}
