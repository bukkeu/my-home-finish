package com.wanted.moneybeetle.domain.course.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 lesson_watch (시청 기록)
 *  */
@Entity
@Table(name = "lesson_watch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lessonWatchId;

    private Long memberId;
    private Long lessonId;
    private int watchedSeconds;
    private boolean isCompleted;
    private LocalDateTime lastWatchedAt;

    private LessonWatch(Long memberId, Long lessonId) {
        this.memberId = memberId;
        this.lessonId = lessonId;
        this.watchedSeconds = 0;
        this.isCompleted = false;
        this.lastWatchedAt = LocalDateTime.now();
    }

    /* 영상을 처음 볼 때 시청 기록을 만든다 */
    public static LessonWatch start(Long memberId, Long lessonId) {
        return new LessonWatch(memberId, lessonId);
    }

    /* 시청 기록을 갱신한다 (LRN-06) */
    public void record(int watchedSeconds, boolean completed) {
        this.watchedSeconds = Math.max(this.watchedSeconds, watchedSeconds); // 본 시간은 줄어들지 않는다
        if (completed) {
            this.isCompleted = true;                                         // 한 번 끝까지 본 영상은 미완료로 돌아가지 않는다
        }
        this.lastWatchedAt = LocalDateTime.now();
    }
}