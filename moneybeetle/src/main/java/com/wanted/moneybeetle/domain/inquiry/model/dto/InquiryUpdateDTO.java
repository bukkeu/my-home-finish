package com.wanted.moneybeetle.domain.inquiry.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 50 문의 수정
 *  */
public record InquiryUpdateDTO(
        @NotBlank(message = "제목을 입력해 주세요")
        @Size(max = 50, message = "제목은 50자까지 쓸 수 있어요") String title,
        @NotBlank(message = "내용을 입력해 주세요")
        @Size(max = 1000, message = "내용은 1,000자까지 쓸 수 있어요") String content
) {
}