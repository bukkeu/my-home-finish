package com.wanted.moneybeetle.domain.course.model.dto;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 152 영상 요약글 수정 요청 { "summary": "..." }
 *   길이(2,000자)·금칙어는 서비스가 API 78(영상 저장)과 같은 기준으로 검사한다. 비우면 요약글이 지워진다
 *  */
public record SummaryUpdateDTO(String summary) {
}
