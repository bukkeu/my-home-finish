package com.wanted.moneybeetle.domain.course.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import lombok.Getter;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 34. 시청 기록 항목
 *  */
@Getter
public class WatchHistoryDTO {

    private final Long lessonId;
    private final String lessonTitle;
    private final Long courseId;
    private final String courseTitle;

    @JsonProperty("isCompleted")
    private final boolean completed;

    private final LocalDateTime lastWatchedAt;

    private WatchHistoryDTO(Long lessonId, String lessonTitle, Long courseId, String courseTitle,
                            boolean completed, LocalDateTime lastWatchedAt) {
        this.lessonId = lessonId;
        this.lessonTitle = lessonTitle;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.completed = completed;
        this.lastWatchedAt = lastWatchedAt;
    }

    public static WatchHistoryDTO from(LessonWatch watch, Lesson lesson, Course course) {
        return new WatchHistoryDTO(lesson.getLessonId(), lesson.getTitle(), course.getCourseId(), course.getTitle(),
                watch.isCompleted(), watch.getLastWatchedAt());
    }
}