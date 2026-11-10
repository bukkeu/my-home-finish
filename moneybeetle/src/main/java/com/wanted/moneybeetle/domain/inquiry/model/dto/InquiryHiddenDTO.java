package com.wanted.moneybeetle.domain.inquiry.model.dto;

import jakarta.validation.constraints.NotNull;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 107 숨김·해제 { "hidden": true }
 *  */
public record InquiryHiddenDTO(
        @NotNull(message = "숨김 여부를 골라 주세요") Boolean hidden
) {
}