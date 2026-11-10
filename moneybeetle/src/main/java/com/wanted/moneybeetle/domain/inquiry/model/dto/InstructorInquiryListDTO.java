package com.wanted.moneybeetle.domain.inquiry.model.dto;

import com.wanted.moneybeetle.global.common.PageResponse;
import java.util.List;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 82 강사 문의함 { waitingCount, items, page, size, totalElements, totalPages }
 *   waitingCount는 필터와 상관없이 내 강의 전체의 답변 대기 수
 *  */
public record InstructorInquiryListDTO(
        long waitingCount,
        List<InstructorInquiryItemDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static InstructorInquiryListDTO of(long waitingCount, PageResponse<InstructorInquiryItemDTO> page) {
        return new InstructorInquiryListDTO(waitingCount, page.getItems(), page.getPage(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
