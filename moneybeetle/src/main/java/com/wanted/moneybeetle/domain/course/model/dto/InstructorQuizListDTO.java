package com.wanted.moneybeetle.domain.course.model.dto;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 150 강사 센터 퀴즈 목록 응답 { "items": [...] } (강사 한 명의 강의라 페이지를 나누지 않는다)
 *  */
public record InstructorQuizListDTO(List<InstructorQuizItemDTO> items) {
}
