package com.wanted.moneybeetle.domain.course.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 lesson_watch (시청 기록)
 *   - 끝까지 봤는지(is_completed)는 화면이 보낸 completed 값이 아니라 서버가 정한다 (#20, 퀴즈 열기·보상 근거)
 *     본 시간은 지난 저장 뒤 실제로 흐른 시간(서버 시계)만큼만 늘어나고, 영상 길이의 90%를 넘으면 완료
 *   - 이어보기 위치(position_seconds)는 영상마다 둔다 (#71). 강의의 마지막 영상은 learning_progress 가 그대로 기억한다
 *  */
@Entity
@Table(name = "lesson_watch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonWatch {

    private static final int MAX_PLAYBACK_RATE = 2;         // 재생 속도 최대 2배까지 인정
    private static final int SLACK_SECONDS = 15;            // 첫 저장 전 재생분·네트워크 지연 여유 (영상당 한 번만)
    private static final double COMPLETE_RATIO = 0.9;       // 영상 길이의 90% 이상 보면 완료

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lessonWatchId;

    private Long memberId;
    private Long lessonId;
    private int watchedSeconds;
    private int positionSeconds;                            // 이 영상의 마지막 재생 위치(초). 되감으면 줄어든다
    private boolean isCompleted;
    private LocalDateTime lastWatchedAt;

    private LessonWatch(Long memberId, Long lessonId, LocalDateTime now) {
        this.memberId = memberId;
        this.lessonId = lessonId;
        this.watchedSeconds = 0;
        this.positionSeconds = 0;
        this.isCompleted = false;
        this.lastWatchedAt = now;
    }

    /* 영상을 처음 볼 때 시청 기록을 만든다 */
    public static LessonWatch start(Long memberId, Long lessonId, LocalDateTime now) {
        return new LessonWatch(memberId, lessonId, now);
    }

    /* 이 영상의 이어보기 위치를 바꾼다 (LRN-05, #71). 범위는 호출하는 쪽이 영상 길이로 맞춰 넘긴다 */
    public void moveTo(int positionSeconds) {
        this.positionSeconds = Math.max(positionSeconds, 0);
    }

    /* 시청 기록을 갱신한다 (LRN-06).
     * 화면이 보낸 본 시간(reportedSeconds)은 지난 저장 뒤 실제로 흐른 시간 × 2배까지만 인정한다.
     * 여유 15초는 영상당 한 번만: 이미 인정된 본 시간만큼 줄어들어서, 요청을 반복해도 다시 생기지 않는다
     * → 한 번에 "다 봤다"고 보내도 실제로 그만큼 시간이 지나야 완료된다 (화면의 completed 값은 쓰지 않는다).
     * 길이가 0 이하인 영상은 완료로 치지 않는다 (시청 근거가 없음) */
    public void record(int reportedSeconds, int durationSeconds, LocalDateTime now) {
        long elapsed = Math.max(0, Duration.between(lastWatchedAt, now).getSeconds());
        long remainingSlack = Math.max(0, SLACK_SECONDS - this.watchedSeconds);
        long maxGain = elapsed * MAX_PLAYBACK_RATE + remainingSlack;
        int credited = (int) Math.min(reportedSeconds, this.watchedSeconds + maxGain);
        this.watchedSeconds = Math.max(this.watchedSeconds, credited);       // 본 시간은 줄어들지 않는다
        if (durationSeconds > 0 && this.watchedSeconds >= durationSeconds * COMPLETE_RATIO) {
            this.isCompleted = true;                                         // 한 번 끝까지 본 영상은 미완료로 돌아가지 않는다
        }
        this.lastWatchedAt = now;
    }
}