package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import lombok.Getter;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 32. 영상 재생 정보
 *  */
@Getter
public class LessonPlayDTO {

    private final Long lessonId;
    private final String title;
    private final String videoUrl;
    private final int durationSeconds;
    private final int positionSeconds;

    private LessonPlayDTO(Long lessonId, String title, String videoUrl, int durationSeconds, int positionSeconds) {
        this.lessonId = lessonId;
        this.title = title;
        this.videoUrl = videoUrl;
        this.durationSeconds = durationSeconds;
        this.positionSeconds = positionSeconds;
    }

    public static LessonPlayDTO from(Lesson lesson, int positionSeconds) {
        return new LessonPlayDTO(lesson.getLessonId(), lesson.getTitle(), lesson.getVideoUrl(),
                lesson.getDurationSeconds(), positionSeconds);
    }
}