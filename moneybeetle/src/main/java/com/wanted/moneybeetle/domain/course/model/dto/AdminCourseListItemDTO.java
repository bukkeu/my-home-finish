package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 89 관리자 강의 목록 한 줄
 *  */
public record AdminCourseListItemDTO(
        Long courseId,
        String title,
        String instructorName,
        String status,
        boolean isPublic,
        LocalDateTime submittedAt,
        long learners
) {
    public static AdminCourseListItemDTO of(Course course, String instructorName, long learners) {
        return new AdminCourseListItemDTO(course.getCourseId(), course.getTitle(), instructorName,
                course.getStatus(), course.isPublic(), course.getSubmittedAt(), learners);
    }
}