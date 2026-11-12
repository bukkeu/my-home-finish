package com.wanted.moneybeetle.domain.course.model.dto;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 152 영상 요약글 수정 응답 { lessonId, courseId, summary, courseStatus }
 *   허가된 강의의 요약글을 고치면 다시 검수(REVIEW)가 되어서(SP-31) 바뀐 강의 상태도 함께 돌려준다
 *  */
public record SummaryUpdatedDTO(Long lessonId, Long courseId, String summary, String courseStatus) {
}
