package com.wanted.moneybeetle.domain.course.model.dto;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 151 강사 센터 요약글 목록 응답 { "items": [...] }
 *  */
public record InstructorSummaryListDTO(List<InstructorSummaryItemDTO> items) {
}
