package com.wanted.moneybeetle.domain.course.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
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
*   테이블 learning_progress (이어보기 위치, 학습 완료)
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#157)
*  */
@Entity
@Table(name = "learning_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningProgress extends BaseTimeEntity {

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

    /* 마지막으로 본 영상과 위치를 바꾼다 (SP-38). 영상별 이어보기 위치는 lesson_watch 에 따로 둔다 (#71) */
    public void moveTo(Long lessonId, int positionSeconds) {
        this.lastLessonId = lessonId;
        this.lastPositionSeconds = positionSeconds;
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /* 학습 완료 (퀴즈 최초 통과, SP-34·LRN-07). 이미 완료였으면 아무것도 바꾸지 않고 false → 보상은 최초 통과 때만 */
    public boolean complete(LocalDateTime completedAt) {
        if (isCompleted()) {
            return false;
        }
        this.status = STATUS_COMPLETED;
        this.completedAt = completedAt;
        return true;
    }
}
