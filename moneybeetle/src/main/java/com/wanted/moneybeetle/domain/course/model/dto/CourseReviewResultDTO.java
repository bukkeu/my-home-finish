package com.wanted.moneybeetle.domain.course.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wanted.moneybeetle.domain.course.model.entity.Course;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 91 응답 { "status": "APPROVED", "isPublic": true } / API 92 응답 { "status": "REJECTED" }
 *  */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseReviewResultDTO(String status, Boolean isPublic) {

    public static CourseReviewResultDTO approved(Course course) {
        return new CourseReviewResultDTO(course.getStatus(), course.isPublic());
    }

    public static CourseReviewResultDTO rejected(Course course) {
        return new CourseReviewResultDTO(course.getStatus(), null);
    }
}