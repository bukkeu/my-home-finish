package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① 신아림 (#71, 마이페이지 Q&A·문의 모음)
 *   API 149 내가 쓴 문의 한 줄. 사이트 문의는 courseId·courseTitle 이 null
 *   courseTitle 은 수강생에게 보이는 강의(허가+공개+미삭제)일 때만 채운다 → 화면은 이 값이 있을 때만 "해당 강의 보러 가기"를 보인다
 *  */
public record MyInquiryItemDTO(
        Long inquiryId,
        Long courseId,
        String courseTitle,
        String category,
        String title,
        String status,
        boolean isHidden,
        LocalDateTime createdAt
) {
    public static MyInquiryItemDTO from(Inquiry inquiry, String title, String courseTitle) {
        return new MyInquiryItemDTO(inquiry.getInquiryId(), inquiry.getCourseId(), courseTitle, inquiry.getCategory(),
                title, inquiry.getStatus(), inquiry.isHidden(), inquiry.getCreatedAt());
    }
}
