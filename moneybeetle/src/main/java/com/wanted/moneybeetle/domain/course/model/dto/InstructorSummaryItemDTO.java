package com.wanted.moneybeetle.domain.course.model.dto;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 151 강사 센터 요약글 목록의 한 줄 (요약글이 있는 영상마다)
 *   published : 강의가 허가(APPROVED)되고 공개 중이면 true ("게시"), 아니면 false ("임시 저장")
 *   thumbnailUrl : 영상에는 썸네일이 없어서 강의의 썸네일을 쓴다
 *  */
public record InstructorSummaryItemDTO(Long lessonId, String lessonTitle, Long courseId, String courseTitle,
                                       String summary, String thumbnailUrl, boolean published,
                                       LocalDateTime updatedAt) {
}
