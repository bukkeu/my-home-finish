package com.wanted.moneybeetle.domain.course.model.dto;

import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import lombok.Getter;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 32. 영상 재생 정보
 *   - positionSeconds : 이어보기 위치 (되감은 위치도 그대로)
 *   - watchedSeconds  : 서버가 인정한 이 영상의 본 시간 (#20). 화면은 이 값에 실제로 재생한 시간만 더해 보낸다 (#52)
 *  */
@Getter
public class LessonPlayDTO {

    private final Long lessonId;
    private final String title;
    private final String videoUrl;
    private final int durationSeconds;
    private final int positionSeconds;
    private final int watchedSeconds;

    private LessonPlayDTO(Long lessonId, String title, String videoUrl, int durationSeconds, int positionSeconds,
                          int watchedSeconds) {
        this.lessonId = lessonId;
        this.title = title;
        this.videoUrl = videoUrl;
        this.durationSeconds = durationSeconds;
        this.positionSeconds = positionSeconds;
        this.watchedSeconds = watchedSeconds;
    }

    public static LessonPlayDTO from(Lesson lesson, int positionSeconds, int watchedSeconds) {
        return new LessonPlayDTO(lesson.getLessonId(), lesson.getTitle(), lesson.getVideoUrl(),
                lesson.getDurationSeconds(), positionSeconds, watchedSeconds);
    }
}