package com.wanted.moneybeetle.domain.inquiry.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 48 문의 작성. 강의 문의는 courseId, 사이트 문의는 category 중 하나만 보낸다
 *  */
public record InquiryCreateDTO(
        Long courseId,
        @Size(max = 30, message = "분류는 30자까지 쓸 수 있어요") String category,
        @NotBlank(message = "제목을 입력해 주세요")
        @Size(max = 50, message = "제목은 50자까지 쓸 수 있어요") String title,
        @NotBlank(message = "내용을 입력해 주세요")
        @Size(max = 1000, message = "내용은 1,000자까지 쓸 수 있어요") String content
) {
    @AssertTrue(message = "강의 문의는 강의를, 사이트 문의는 분류를 하나만 골라 주세요")
    public boolean isTargetValid() {
        boolean hasCategory = category != null && !category.isBlank();
        return (courseId != null) != hasCategory;
    }
}