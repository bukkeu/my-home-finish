package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 35. 학습 완료 목록 항목
 *  */
@Getter
public class CompletedCourseDTO {

    private final Long courseId;
    private final String title;
    private final LocalDateTime completedAt;

    private CompletedCourseDTO(Long courseId, String title, LocalDateTime completedAt) {
        this.courseId = courseId;
        this.title = title;
        this.completedAt = completedAt;
    }

    public static CompletedCourseDTO from(LearningProgress progress, Course course) {
        return new CompletedCourseDTO(course.getCourseId(), course.getTitle(), progress.getCompletedAt());
    }
}