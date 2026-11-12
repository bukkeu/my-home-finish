package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;

/* comment.
 *   담당 : DBA ① (강의·퀴즈) — #369 는 신아림이 나눠 준 작업 (이재성)
 *   API 76-1 강사 공개 켜기/끄기 응답 { "courseId": 7, "status": "APPROVED", "isPublic": false }
 *  */
public record CourseVisibilityResultDTO(Long courseId, String status, boolean isPublic) {

    public static CourseVisibilityResultDTO from(Course course) {
        return new CourseVisibilityResultDTO(course.getCourseId(), course.getStatus(), course.isPublic());
    }
}
