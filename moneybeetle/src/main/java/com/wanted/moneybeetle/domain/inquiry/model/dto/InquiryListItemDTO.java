package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 47 문의 목록 한 줄
 *  */
public record InquiryListItemDTO(
        Long inquiryId,
        Long courseId,
        String category,
        String title,
        String status,
        String writerNickname,
        LocalDateTime createdAt
) {
    public static InquiryListItemDTO from(Inquiry inquiry, String title, String writerNickname) {
        return new InquiryListItemDTO(inquiry.getInquiryId(), inquiry.getCourseId(), inquiry.getCategory(),
                title, inquiry.getStatus(), writerNickname, inquiry.getCreatedAt());
    }
}