package com.wanted.moneybeetle.domain.course.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   API 78 응답 { "lessonIds": [20, 21] } - 요청 lessons와 같은 순서 (기존 영상은 보낸 ID, 새 영상은 새 ID)
*  */
public record LessonIdsDTO(List<Long> lessonIds) {
}
