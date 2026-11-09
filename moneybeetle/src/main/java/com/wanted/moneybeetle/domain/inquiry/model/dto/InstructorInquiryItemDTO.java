package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 82 강사 문의함 한 줄
 *  */
public record InstructorInquiryItemDTO(
        Long inquiryId,
        Long courseId,
        String courseTitle,
        String title,
        String status,
        LocalDateTime createdAt
) {
    public static InstructorInquiryItemDTO of(Inquiry inquiry, String courseTitle) {
        return new InstructorInquiryItemDTO(inquiry.getInquiryId(), inquiry.getCourseId(), courseTitle,
                inquiry.getTitle(), inquiry.getStatus(), inquiry.getCreatedAt());
    }
}
