package com.wanted.moneybeetle.domain.course.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ① 신아림 (#295)
*   강의 상태 enum: 목록 필터 값 거르기, 상태 흐름(DRAFT → REVIEW → APPROVED/REJECTED), 화면용 문자열이 예전과 같은지
*  */
class CourseStatusTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

    private static Course draft() {
        return Course.create(2L, 21L, "청약통장 기초", "소개", null, null);
    }

    @Test
    @DisplayName("목록 필터: 아는 값만 그 상태로, 비었거나 모르는 값(소문자 포함)은 거르지 않는다(null)")
    void filterOf() {
        assertThat(CourseStatus.filterOf("REVIEW")).isEqualTo(CourseStatus.REVIEW);
        assertThat(CourseStatus.filterOf("REJECTED")).isEqualTo(CourseStatus.REJECTED);
        assertThat(CourseStatus.filterOf(null)).isNull();
        assertThat(CourseStatus.filterOf("")).isNull();
        assertThat(CourseStatus.filterOf("review")).isNull();
        assertThat(CourseStatus.filterOf("ALL")).isNull();
    }

    @Test
    @DisplayName("새 강의는 DRAFT 이고, 화면·JSON 이 읽는 getStatus() 는 예전과 같은 문자열이다")
    void statusStringUnchanged() {
        Course course = draft();

        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(course.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("검수 요청 → 반려면 isRejected, 반려된 강의는 다시 검수 요청할 수 있다")
    void rejectAndRequestAgain() {
        Course course = draft();
        course.requestReview(NOW);
        assertThat(course.isInReview()).isTrue();

        course.reject();
        assertThat(course.isRejected()).isTrue();
        assertThat(course.getStatus()).isEqualTo("REJECTED");

        course.requestReview(NOW.plusDays(1));
        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.REVIEW);
    }

    @Test
    @DisplayName("허가하면 APPROVED + 공개라 학습자에게 보이고, 허가된 강의는 바로 검수 요청할 수 없다")
    void approve() {
        Course course = draft();
        course.requestReview(NOW);
        course.approve(NOW);

        assertThat(course.isApproved()).isTrue();
        assertThat(course.isRejected()).isFalse();
        assertThat(course.isVisibleToLearner()).isTrue();
        assertThatThrownBy(() -> course.requestReview(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CourseErrorCode.COURSE_NOT_REQUESTABLE);
    }

    @Test
    @DisplayName("허가된 강의를 고치면 다시 REVIEW 가 되어 학습자에게 보이지 않는다")
    void editApprovedGoesBackToReview() {
        Course course = draft();
        course.requestReview(NOW);
        course.approve(NOW);

        course.change(21L, "새 제목", "소개", null, null, NOW.plusDays(1));

        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.REVIEW);
        assertThat(course.isVisibleToLearner()).isFalse();
    }
}
