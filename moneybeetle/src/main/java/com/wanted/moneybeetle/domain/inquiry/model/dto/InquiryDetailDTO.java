package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;

import java.time.LocalDateTime;
import java.util.List;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 49 문의 상세 (답글 포함)
 *  */
public record InquiryDetailDTO(
        Long inquiryId,
        Long courseId,
        String category,
        String title,
        String content,
        String status,
        boolean isHidden,
        Writer writer,
        LocalDateTime createdAt,
        List<Reply> replies
) {
    public record Writer(Long memberId, String nickname, String role) {
    }

    public record Reply(Long replyId, Writer writer, String content, LocalDateTime createdAt) {
    }

    public static InquiryDetailDTO of(Inquiry inquiry, String title, String content,
                                      Writer writer, List<Reply> replies) {
        return new InquiryDetailDTO(inquiry.getInquiryId(), inquiry.getCourseId(), inquiry.getCategory(),
                title, content, inquiry.getStatus(), inquiry.isHidden(), writer, inquiry.getCreatedAt(), replies);
    }
}