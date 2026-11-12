package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorQuizItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorSummaryItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.SummaryUpdateDTO;
import com.wanted.moneybeetle.domain.course.model.dto.SummaryUpdatedDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.InstructorQuizService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① (강의·퀴즈) - 강사 센터 화면 작업을 DBA ② 김예린이 나눠 받아 만들었다 (#366)
*   강사 센터 퀴즈·요약글 목록(API 150·151)과 요약글 수정(API 152): 본인 강의만 보이는지, 남의 영상 수정 403, 검수 중 수정 거부 등
*   저장소와 다른 도메인 서비스는 가짜(mock)로 둔다
*  */
class InstructorContentServiceTest {

    private static final Long ME = 5L;
    private static final Long OTHER = 6L;

    private CourseRepository courseRepository;
    private LessonRepository lessonRepository;
    private InstructorQuizService instructorQuizService;
    private ForbiddenWordService forbiddenWordService;
    private InstructorContentService service;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        lessonRepository = mock(LessonRepository.class);
        instructorQuizService = mock(InstructorQuizService.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        service = new InstructorContentService(courseRepository, lessonRepository, instructorQuizService,
                forbiddenWordService, Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private Course course(long id, long instructorId, String title, CourseStatus status, boolean isPublic) {
        Course course = Course.create(instructorId, 21L, title, "소개", null, null);
        ReflectionTestUtils.setField(course, "courseId", id);
        ReflectionTestUtils.setField(course, "status", status);
        ReflectionTestUtils.setField(course, "isPublic", isPublic);
        ReflectionTestUtils.setField(course, "thumbnailUrl", "/uploads/c" + id + ".png");
        return course;
    }

    private Lesson lesson(long id, long courseId, String summary, LocalDateTime updatedAt) {
        Lesson lesson = Lesson.create(courseId, 1, "영상" + id, "/uploads/v" + id + ".mp4", 120, summary);
        ReflectionTestUtils.setField(lesson, "lessonId", id);
        ReflectionTestUtils.setField(lesson, "updatedAt", updatedAt);
        return lesson;
    }

    /* ===== 퀴즈 목록 (API 150) ===== */

    @Test
    @DisplayName("퀴즈 목록: 내 강의 번호만 quiz 쪽에 물어보고, 문항이 없는 강의는 빼고, 가장 늦게 바뀐 순으로 돌려준다")
    void quizzesOfMyCoursesOnly() {
        when(courseRepository.findByInstructorIdAndDeletedAtIsNull(ME)).thenReturn(List.of(
                course(1, ME, "청약 기초", CourseStatus.APPROVED, true),
                course(2, ME, "ETF 입문", CourseStatus.DRAFT, false),
                course(3, ME, "퀴즈 없는 강의", CourseStatus.DRAFT, false)));
        when(instructorQuizService.findQuizStats(any())).thenReturn(List.of(
                new CourseQuizStatDTO(1L, 5, 8, LocalDateTime.of(2026, 11, 1, 9, 0)),
                new CourseQuizStatDTO(2L, 5, 0, LocalDateTime.of(2026, 11, 5, 9, 0))));

        List<InstructorQuizItemDTO> items = service.findQuizzes(ME).items();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> asked = ArgumentCaptor.forClass(List.class);
        verify(instructorQuizService).findQuizStats(asked.capture());
        assertThat(asked.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);   // 남의 강의 번호는 들어가지 않는다
        assertThat(items).extracting(InstructorQuizItemDTO::courseId).containsExactly(2L, 1L);   // 최근 수정 순, 3번(문항 없음) 제외
        assertThat(items.get(1)).isEqualTo(new InstructorQuizItemDTO(1L, "청약 기초", 5, 8, LocalDateTime.of(2026, 11, 1, 9, 0)));
    }

    @Test
    @DisplayName("퀴즈 목록: 문항 수가 0 인 통계는 목록에서 뺀다")
    void zeroCountIsDropped() {
        when(courseRepository.findByInstructorIdAndDeletedAtIsNull(ME))
                .thenReturn(List.of(course(1, ME, "청약 기초", CourseStatus.DRAFT, false)));
        when(instructorQuizService.findQuizStats(any()))
                .thenReturn(List.of(new CourseQuizStatDTO(1L, 0, 0, LocalDateTime.of(2026, 11, 1, 9, 0))));

        assertThat(service.findQuizzes(ME).items()).isEmpty();
    }

    /* ===== 요약글 목록 (API 151) ===== */

    @Test
    @DisplayName("요약글 목록: 내 강의의 영상만 (강의 번호를 한 번에 넘긴다). 허가+공개 강의만 published 이고 썸네일은 강의 것")
    void summariesOfMyCourses() {
        when(courseRepository.findByInstructorIdAndDeletedAtIsNull(ME)).thenReturn(List.of(
                course(1, ME, "청약 기초", CourseStatus.APPROVED, true),
                course(2, ME, "ETF 입문", CourseStatus.APPROVED, false),
                course(3, ME, "주식 입문", CourseStatus.REVIEW, true)));
        when(lessonRepository.findWithSummaryByCourseIds(any())).thenReturn(List.of(
                lesson(11, 1, "요약 1", LocalDateTime.of(2026, 11, 3, 9, 0)),
                lesson(12, 2, "요약 2", LocalDateTime.of(2026, 11, 2, 9, 0)),
                lesson(13, 3, "요약 3", LocalDateTime.of(2026, 11, 1, 9, 0))));

        List<InstructorSummaryItemDTO> items = service.findSummaries(ME).items();

        assertThat(items).extracting(InstructorSummaryItemDTO::lessonId, InstructorSummaryItemDTO::published)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, true), org.assertj.core.groups.Tuple.tuple(12L, false),
                        org.assertj.core.groups.Tuple.tuple(13L, false));
        assertThat(items.get(0)).isEqualTo(new InstructorSummaryItemDTO(11L, "영상11", 1L, "청약 기초", "요약 1",
                "/uploads/c1.png", true, LocalDateTime.of(2026, 11, 3, 9, 0)));
        verify(lessonRepository).findWithSummaryByCourseIds(org.mockito.ArgumentMatchers.argThat(
                ids -> ids.size() == 3 && ids.containsAll(List.of(1L, 2L, 3L))));
    }

    @Test
    @DisplayName("요약글 목록: 내 강의가 없으면 영상을 조회하지 않고 빈 목록")
    void noCoursesNoLessonQuery() {
        when(courseRepository.findByInstructorIdAndDeletedAtIsNull(ME)).thenReturn(List.of());

        assertThat(service.findSummaries(ME).items()).isEmpty();
        verify(lessonRepository, never()).findWithSummaryByCourseIds(any());
    }

    /* ===== 요약글 수정 (API 152) ===== */

    private Lesson givenLesson(Course course, long lessonId) {
        Lesson lesson = lesson(lessonId, course.getCourseId(), "예전 요약", LocalDateTime.of(2026, 11, 1, 9, 0));
        when(lessonRepository.findCourseIdById(lessonId)).thenReturn(Optional.of(course.getCourseId()));
        when(lessonRepository.findByIdForUpdate(lessonId)).thenReturn(Optional.of(lesson));
        when(courseRepository.findByIdForUpdate(course.getCourseId())).thenReturn(Optional.of(course));
        return lesson;
    }

    @Test
    @DisplayName("요약글 수정: 강의를 먼저 잠그고 그 뒤에 영상을 잠가 읽는다 (잠금을 기다리는 동안 낡은 영상 값으로 비교하지 않게)")
    void locksCourseBeforeReadingLesson() {
        Course course = course(1, ME, "청약 기초", CourseStatus.DRAFT, false);
        givenLesson(course, 11);

        service.updateSummary(ME, 11L, new SummaryUpdateDTO("새 요약"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(lessonRepository, courseRepository);
        order.verify(lessonRepository).findCourseIdById(11L);
        order.verify(courseRepository).findByIdForUpdate(1L);
        order.verify(lessonRepository).findByIdForUpdate(11L);
        org.mockito.Mockito.verify(lessonRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    @DisplayName("요약글 수정: 앞뒤 공백을 빼고 저장하고, 영상 제목·파일·길이는 그대로 둔다. 초안 강의는 상태가 그대로")
    void updatesOnlySummary() {
        Course course = course(1, ME, "청약 기초", CourseStatus.DRAFT, false);
        Lesson lesson = givenLesson(course, 11);

        SummaryUpdatedDTO result = service.updateSummary(ME, 11L, new SummaryUpdateDTO("  새 요약  "));

        assertThat(lesson.getSummary()).isEqualTo("새 요약");
        assertThat(lesson.getTitle()).isEqualTo("영상11");
        assertThat(lesson.getVideoUrl()).isEqualTo("/uploads/v11.mp4");
        assertThat(lesson.getDurationSeconds()).isEqualTo(120);
        assertThat(result).isEqualTo(new SummaryUpdatedDTO(11L, 1L, "새 요약", "DRAFT"));
    }

    @Test
    @DisplayName("요약글 수정: 허가된 강의를 고치면 다시 검수(REVIEW)가 되고 응답에 바뀐 상태가 나온다 (API 78과 같은 규칙)")
    void approvedCourseGoesBackToReview() {
        Course course = course(1, ME, "청약 기초", CourseStatus.APPROVED, true);
        givenLesson(course, 11);

        SummaryUpdatedDTO result = service.updateSummary(ME, 11L, new SummaryUpdateDTO("새 요약"));

        assertThat(result.courseStatus()).isEqualTo("REVIEW");
        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.REVIEW);
    }

    @Test
    @DisplayName("요약글 수정: 기존과 같은 글이면 허가된 강의도 다시 검수로 돌리지 않고 그대로 돌려준다")
    void sameSummaryDoesNotReReview() {
        Course course = course(1, ME, "청약 기초", CourseStatus.APPROVED, true);
        Lesson lesson = givenLesson(course, 11);

        SummaryUpdatedDTO result = service.updateSummary(ME, 11L, new SummaryUpdateDTO("  예전 요약  "));

        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.APPROVED);
        assertThat(lesson.getSummary()).isEqualTo("예전 요약");
        assertThat(result).isEqualTo(new SummaryUpdatedDTO(11L, 1L, "예전 요약", "APPROVED"));
    }

    @Test
    @DisplayName("요약글 수정: 같은 글이어도 검수 중(REVIEW)인 강의는 거절한다 (COURSE_IN_REVIEW)")
    void sameSummaryInReviewIsStillRejected() {
        Course course = course(1, ME, "청약 기초", CourseStatus.REVIEW, false);
        givenLesson(course, 11);

        assertThatThrownBy(() -> service.updateSummary(ME, 11L, new SummaryUpdateDTO("예전 요약")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CourseErrorCode.COURSE_IN_REVIEW));
    }

    @Test
    @DisplayName("요약글 수정: 빈 글(공백)이나 null 은 요약글을 지운다")
    void blankClearsSummary() {
        Course course = course(1, ME, "청약 기초", CourseStatus.DRAFT, false);
        Lesson lesson = givenLesson(course, 11);

        service.updateSummary(ME, 11L, new SummaryUpdateDTO("   "));
        assertThat(lesson.getSummary()).isNull();

        lesson.change("영상11", "/uploads/v11.mp4", 120, "다시 씀");
        service.updateSummary(ME, 11L, new SummaryUpdateDTO(null));
        assertThat(lesson.getSummary()).isNull();
    }

    @Test
    @DisplayName("요약글 수정: 남의 강의 영상이면 403(FORBIDDEN)이고 아무것도 바뀌지 않는다")
    void otherInstructorsLessonIsForbidden() {
        Course course = course(1, OTHER, "남의 강의", CourseStatus.APPROVED, true);
        Lesson lesson = givenLesson(course, 11);

        assertThatThrownBy(() -> service.updateSummary(ME, 11L, new SummaryUpdateDTO("가로채기")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(lesson.getSummary()).isEqualTo("예전 요약");
        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.APPROVED);
    }

    @Test
    @DisplayName("요약글 수정: 검수 중(REVIEW)인 강의는 고칠 수 없다 (COURSE_IN_REVIEW)")
    void courseInReviewIsRejected() {
        Course course = course(1, ME, "청약 기초", CourseStatus.REVIEW, false);
        Lesson lesson = givenLesson(course, 11);

        assertThatThrownBy(() -> service.updateSummary(ME, 11L, new SummaryUpdateDTO("새 요약")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CourseErrorCode.COURSE_IN_REVIEW));

        assertThat(lesson.getSummary()).isEqualTo("예전 요약");
    }

    @Test
    @DisplayName("요약글 수정: 2,000자는 되고 2,001자는 SUMMARY_TOO_LONG. 거절하면 강의 상태도 그대로")
    void lengthLimit() {
        Course course = course(1, ME, "청약 기초", CourseStatus.APPROVED, true);
        givenLesson(course, 11);

        assertThat(service.updateSummary(ME, 11L, new SummaryUpdateDTO("가".repeat(2000))).summary()).hasSize(2000);

        Course another = course(2, ME, "ETF 입문", CourseStatus.APPROVED, true);
        givenLesson(another, 12);
        assertThatThrownBy(() -> service.updateSummary(ME, 12L, new SummaryUpdateDTO("가".repeat(2001))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CourseErrorCode.SUMMARY_TOO_LONG));
        assertThat(another.getCourseStatus()).isEqualTo(CourseStatus.APPROVED);
    }

    @Test
    @DisplayName("요약글 수정: 금칙어가 있으면 거절하고 강의 상태는 그대로")
    void forbiddenWordIsRejected() {
        Course course = course(1, ME, "청약 기초", CourseStatus.APPROVED, true);
        Lesson lesson = givenLesson(course, 11);
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN_WORD)).when(forbiddenWordService).check("나쁜말");

        assertThatThrownBy(() -> service.updateSummary(ME, 11L, new SummaryUpdateDTO("나쁜말")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN_WORD));

        assertThat(lesson.getSummary()).isEqualTo("예전 요약");
        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.APPROVED);
    }

    @Test
    @DisplayName("요약글 수정: 없는 영상은 LESSON_NOT_FOUND, 삭제된 강의의 영상은 NOT_FOUND")
    void missingLessonOrDeletedCourse() {
        when(lessonRepository.findCourseIdById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSummary(ME, 99L, new SummaryUpdateDTO("x")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CourseErrorCode.LESSON_NOT_FOUND));

        Course deleted = course(1, ME, "삭제된 강의", CourseStatus.DRAFT, false);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 11, 1, 9, 0));
        givenLesson(deleted, 11);
        assertThatThrownBy(() -> service.updateSummary(ME, 11L, new SummaryUpdateDTO("x")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
