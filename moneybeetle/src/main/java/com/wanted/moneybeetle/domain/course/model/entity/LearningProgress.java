package com.wanted.moneybeetle.domain.course.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 learning_progress (이어보기 위치, 학습 완료)
*  */
@Entity
@Table(name = "learning_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningProgress {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long learningProgressId;

    private Long memberId;
    private Long courseId;
    private String status;
    private Long lastLessonId;
    private int lastPositionSeconds;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LearningProgress(Long memberId, Long courseId) {
        this.memberId = memberId;
        this.courseId = courseId;
        this.status = STATUS_IN_PROGRESS;
        this.lastPositionSeconds = 0;
    }

    /* 강의를 처음 재생할 때 진행 기록을 만든다 */
    public static LearningProgress start(Long memberId, Long courseId) {
        return new LearningProgress(memberId, courseId);
    }

    /* 마지막으로 본 영상과 위치를 바꾼다 (SP-38) */
    public void moveTo(Long lessonId, int positionSeconds) {
        this.lastLessonId = lessonId;
        this.lastPositionSeconds = positionSeconds;
    }

    /* 이 영상의 이어보기 위치. 마지막으로 본 영상이 아니면 처음부터 */
    public int positionOf(Long lessonId) {
        return lessonId.equals(lastLessonId) ? lastPositionSeconds : 0;
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    // TODO 공통 설정(SCM)의 BaseTimeEntity가 생기면 상속으로 바꾸기
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
