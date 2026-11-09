package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   강의 ID + 상태 응답 { "courseId": 7, "status": "REVIEW" } (API 81, #30의 73·75에서도 사용)
 *  */
public record CourseStatusDTO(Long courseId, String status) {

    public static CourseStatusDTO from(Course course) {
        return new CourseStatusDTO(course.getCourseId(), course.getStatus());
    }
}
