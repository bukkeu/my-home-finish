package com.wanted.moneybeetle.domain.course.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ① 신아림 (#20, 영상 완료 판정)
*   영상을 끝까지 봤는지는 화면이 보낸 값이 아니라 서버 시계로 정한다 (LessonWatch.record).
*   - 본 시간은 지난 저장 뒤 실제로 흐른 시간 × 2배까지만 늘어난다. 여유 15초는 영상당 한 번만
*   - 영상 길이의 90% 이상이면 완료, 한 번 완료하면 미완료로 돌아가지 않는다
*  */
class LessonWatchTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 11, 10, 0, 0);
    private static final int DURATION = 100;                 // 영상 길이 100초 → 90초 이상 보면 완료

    private LessonWatch watch;

    @BeforeEach
    void setUp() {
        watch = LessonWatch.start(8L, 21L, T0);
    }

    @Test
    @DisplayName("10초 만에 '다 봤다'고 보내도 10초 × 2배 + 여유 15초 = 35초만 인정하고 완료가 아니다")
    void reportedSecondsAreCappedByServerClock() {
        watch.record(DURATION, DURATION, T0.plusSeconds(10));

        assertThat(watch.getWatchedSeconds()).isEqualTo(35);
        assertThat(watch.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("실제로 시간이 흐르면 보낸 값을 그대로 인정하고, 90%를 넘으면 완료")
    void completesWhenEnoughTimePassed() {
        watch.record(95, DURATION, T0.plusSeconds(60));

        assertThat(watch.getWatchedSeconds()).isEqualTo(95);
        assertThat(watch.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("90% 경계: 89초는 미완료, 90초는 완료")
    void completeRatioBoundary() {
        watch.record(89, DURATION, T0.plusSeconds(60));
        assertThat(watch.isCompleted()).isFalse();

        watch.record(90, DURATION, T0.plusSeconds(120));
        assertThat(watch.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("여유 15초는 영상당 한 번만: 시간이 흐르지 않은 채 요청을 반복해도 본 시간이 늘지 않는다")
    void slackIsGrantedOnlyOnce() {
        watch.record(DURATION, DURATION, T0);
        assertThat(watch.getWatchedSeconds()).isEqualTo(15);

        for (int i = 0; i < 10; i++) {
            watch.record(DURATION, DURATION, T0);
        }

        assertThat(watch.getWatchedSeconds()).isEqualTo(15);
        assertThat(watch.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("서버 시각이 지난 저장보다 앞서도(시계 역행) 본 시간이 줄거나 음수 이득이 생기지 않는다")
    void clockGoingBackwardsGivesNoGain() {
        watch.record(40, DURATION, T0.plusSeconds(30));
        assertThat(watch.getWatchedSeconds()).isEqualTo(40);

        watch.record(DURATION, DURATION, T0.plusSeconds(10));

        assertThat(watch.getWatchedSeconds()).isEqualTo(40);
    }

    @Test
    @DisplayName("본 시간은 줄어들지 않고, 한 번 완료한 영상은 되감아도 완료로 남는다")
    void watchedSecondsAndCompletionNeverGoBack() {
        watch.record(DURATION, DURATION, T0.plusSeconds(60));
        assertThat(watch.isCompleted()).isTrue();

        watch.record(5, DURATION, T0.plusSeconds(120));

        assertThat(watch.getWatchedSeconds()).isEqualTo(DURATION);
        assertThat(watch.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("길이가 0인 영상은 시청 근거가 없으므로 완료로 치지 않는다")
    void zeroLengthLessonNeverCompletes() {
        watch.record(0, 0, T0.plusSeconds(60));

        assertThat(watch.isCompleted()).isFalse();
    }
}
