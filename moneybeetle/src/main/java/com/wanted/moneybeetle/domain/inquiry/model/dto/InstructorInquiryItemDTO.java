package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 82 강사 문의함 한 줄. writerNickname = 질문한 학생 닉네임 (탈퇴 회원은 "탈퇴한 사용자", #370)
 *  */
public record InstructorInquiryItemDTO(
        Long inquiryId,
        Long courseId,
        String courseTitle,
        String title,
        String writerNickname,
        String status,
        LocalDateTime createdAt
) {
    public static InstructorInquiryItemDTO of(Inquiry inquiry, String courseTitle, String writerNickname) {
        return new InstructorInquiryItemDTO(inquiry.getInquiryId(), inquiry.getCourseId(), courseTitle,
                inquiry.getTitle(), writerNickname, inquiry.getStatus(), inquiry.getCreatedAt());
    }
}
