package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 106 관리자 문의 목록 한 줄. 관리자는 숨긴 글도 원문 제목을 본다
 *  */
public record AdminInquiryListItemDTO(
        Long inquiryId,
        Long courseId,
        String category,
        String title,
        String status,
        Long writerId,
        String writerNickname,
        boolean isHidden,
        boolean isDeleted,
        LocalDateTime createdAt
) {
    public static AdminInquiryListItemDTO of(Inquiry inquiry, String writerNickname) {
        return new AdminInquiryListItemDTO(inquiry.getInquiryId(), inquiry.getCourseId(), inquiry.getCategory(),
                inquiry.getTitle(), inquiry.getStatus(), inquiry.getMemberId(), writerNickname,
                inquiry.isHidden(), inquiry.getDeletedAt() != null, inquiry.getCreatedAt());
    }
}