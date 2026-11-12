package com.wanted.moneybeetle.domain.inquiry.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* comment.
 *   담당 : PM 이재성 (#370, 신아림 담당 inquiry 도메인에서 나눠 받은 작업)
 *   API 52-1 답변 수정 (답글 작성 API 52 와 같은 검증)
 *  */
public record InquiryReplyUpdateDTO(
        @NotBlank(message = "내용을 입력해 주세요")
        @Size(max = 1000, message = "내용은 1,000자까지 쓸 수 있어요") String content
) {
}
