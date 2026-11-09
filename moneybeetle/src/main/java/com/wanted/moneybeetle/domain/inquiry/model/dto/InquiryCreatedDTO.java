package com.wanted.moneybeetle.domain.inquiry.model.dto;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 48 응답 { "inquiryId": 12 } / API 52 응답 { "replyId": 5 }
 *  */
public record InquiryCreatedDTO(Long inquiryId, Long replyId) {

    public static InquiryCreatedDTO ofInquiry(Long inquiryId) {
        return new InquiryCreatedDTO(inquiryId, null);
    }

    public static InquiryCreatedDTO ofReply(Long replyId) {
        return new InquiryCreatedDTO(null, replyId);
    }
}