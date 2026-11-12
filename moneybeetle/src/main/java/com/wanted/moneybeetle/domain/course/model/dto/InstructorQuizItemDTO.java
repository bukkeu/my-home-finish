package com.wanted.moneybeetle.domain.course.model.dto;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 150 강사 센터 퀴즈 목록의 한 줄 (강의마다) { courseId, courseTitle, testCount, mainCount, updatedAt }
 *  */
public record InstructorQuizItemDTO(Long courseId, String courseTitle, long testCount, long mainCount,
                                    LocalDateTime updatedAt) {
}
