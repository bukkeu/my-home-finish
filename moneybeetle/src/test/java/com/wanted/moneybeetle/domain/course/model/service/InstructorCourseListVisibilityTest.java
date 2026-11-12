package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CourseSaveDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseVisibilityResultDTO;
import com.wanted.moneybeetle.domain.course.model.dto.MyCourseItemDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseLevel;
import com.wanted.moneybeetle.domain.inquiry.model.service.InstructorInquiryService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.quiz.model.service.InstructorQuizService;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.file.FileStorageService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM 이재성 (#369, course 도메인 담당 신아림이 나눠 준 작업)
*   강사 내 강의 목록(API 72)의 새 항목, 난이도 저장(API 73·75), 공개 켜기/끄기(API 76-1)
*   - 목록 : 카테고리 이름(소주제·상위)·난이도·영상 수·재생 시간 합·등록일. 영상·카테고리는 강의 수와 상관없이 한 번에 모아 조회
*   - 난이도 : 안 보내면 입문(BEGINNER), 네 값이 아니면 입력 검사에서 거절
*   - 공개 전환 : 허가된 본인 강의만, 상태는 그대로(다시 검수하지 않음). 미허가 409, 남의 강의 403, 삭제된 강의 404
*  */
class InstructorCourseListVisibilityTest {

    private static final Long ME = 2L;
    private static final Long OTHER = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

    private CourseRepository courseRepository;
    private CategoryRepository categoryRepository;
    private InstructorCourseService service;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        LearningProgressRepository learningProgressRepository = mock(LearningProgressRepository.class);
        InstructorInquiryService inquiryService = mock(InstructorInquiryService.class);
        when(inquiryService.countWaitingByCourse(anyCollection())).thenReturn(Map.of());
        service = new InstructorCourseService(courseRepository, categoryRepository, mock(LessonRepository.class),
                learningProgressRepository, mock(CourseReviewRepository.class), mock(QuizService.class),
                mock(InstructorQuizService.class), inquiryService, mock(ForbiddenWordService.class),
                mock(FileStorageService.class),
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("목록에 소주제·상위 카테고리 이름, 난이도, 영상 수·재생 시간 합, 등록일이 들어간다")
    void listHasNewFields() {
        Category realEstate = category(2L, Category.createCategory("부동산", 1));
        Category account = category(21L, Category.createSubCategory(realEstate, "청약통장", 1));
        Course withLessons = course(7L, ME, 21L, CourseLevel.INTERMEDIATE);
        ReflectionTestUtils.setField(withLessons, "createdAt", NOW);
        Course noLessons = course(8L, ME, 21L, CourseLevel.BEGINNER);
        when(courseRepository.findMyCourses(ME, null)).thenReturn(List.of(noLessons, withLessons));
        when(courseRepository.findLessonStatsByCourseIds(anyList())).thenReturn(List.of(stat(7L, 3, 1520)));
        when(categoryRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            return java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                    .map(id -> id.equals(21L) ? account : id.equals(2L) ? realEstate : null)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        });

        List<MyCourseItemDTO> items = service.findMyCourses(ME, null).items();

        MyCourseItemDTO item = items.get(1);
        assertThat(item.courseId()).isEqualTo(7L);
        assertThat(item.categoryId()).isEqualTo(21L);
        assertThat(item.categoryName()).isEqualTo("청약통장");
        assertThat(item.parentCategoryName()).isEqualTo("부동산");
        assertThat(item.level()).isEqualTo("INTERMEDIATE");
        assertThat(item.lessonCount()).isEqualTo(3);
        assertThat(item.totalDurationSeconds()).isEqualTo(1520);
        assertThat(item.createdAt()).isEqualTo(NOW);
        // 영상이 없는 강의는 0개·0초
        assertThat(items.get(0).lessonCount()).isZero();
        assertThat(items.get(0).totalDurationSeconds()).isZero();
        assertThat(items.get(0).level()).isEqualTo("BEGINNER");
        // 강의가 여러 개여도 영상 집계는 한 번, 카테고리는 소주제·상위 두 번
        verify(courseRepository, times(1)).findLessonStatsByCourseIds(anyList());
        verify(categoryRepository, times(2)).findAllById(any());
    }

    @Test
    @DisplayName("난이도를 안 보내면 입문(BEGINNER), 보내면 그 값으로 만든다")
    void createUsesLevel() {
        Category realEstate = category(2L, Category.createCategory("부동산", 1));
        when(categoryRepository.findById(21L))
                .thenReturn(Optional.of(category(21L, Category.createSubCategory(realEstate, "청약통장", 1))));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createCourse(ME, new CourseSaveDTO(21L, "청약 기초", "소개", null, null, null));
        service.createCourse(ME, new CourseSaveDTO(21L, "청약 심화", "소개", "ADVANCED", null, null));

        ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Course::getLevel)
                .containsExactly(CourseLevel.BEGINNER, CourseLevel.ADVANCED);
    }

    @Test
    @DisplayName("난이도가 네 값이 아니면 입력 검사에서 거절한다")
    void invalidLevelRejected() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new CourseSaveDTO(21L, "제목", null, "EXPERT", null, null)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("level");
        assertThat(validator.validate(new CourseSaveDTO(21L, "제목", null, "ELEMENTARY", null, null))).isEmpty();
        assertThat(validator.validate(new CourseSaveDTO(21L, "제목", null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("허가된 본인 강의는 공개를 끄고 켤 수 있고, 상태는 APPROVED 그대로다")
    void approvedCourseToggles() {
        Course course = approvedCourse(7L, ME);
        when(courseRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(course));

        CourseVisibilityResultDTO off = service.changeVisibility(ME, 7L, false);
        assertThat(off).isEqualTo(new CourseVisibilityResultDTO(7L, "APPROVED", false));

        CourseVisibilityResultDTO on = service.changeVisibility(ME, 7L, true);
        assertThat(on).isEqualTo(new CourseVisibilityResultDTO(7L, "APPROVED", true));
        assertThat(course.isVisibleToLearner()).isTrue();
    }

    @Test
    @DisplayName("허가 안 된 강의는 COURSE_NOT_APPROVED, 공개 여부는 그대로")
    void notApprovedRejected() {
        Course draft = course(7L, ME, 21L, CourseLevel.BEGINNER);
        when(courseRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(draft));

        assertError(() -> service.changeVisibility(ME, 7L, true), CourseErrorCode.COURSE_NOT_APPROVED);
        assertThat(draft.isPublic()).isFalse();
    }

    @Test
    @DisplayName("남의 강의는 FORBIDDEN, 삭제된 강의는 NOT_FOUND")
    void othersAndDeleted() {
        Course others = approvedCourse(7L, OTHER);
        Course deleted = approvedCourse(8L, ME);
        deleted.delete(NOW);
        when(courseRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(others));
        when(courseRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(deleted));

        assertError(() -> service.changeVisibility(ME, 7L, false), CommonErrorCode.FORBIDDEN);
        assertThat(others.isPublic()).isTrue();
        assertError(() -> service.changeVisibility(ME, 8L, true), CommonErrorCode.NOT_FOUND);
    }

    private static void assertError(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    private static Category category(Long id, Category category) {
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }

    private static Course course(Long id, Long instructorId, Long categoryId, CourseLevel level) {
        Course course = Course.create(instructorId, categoryId, "강의 " + id, "소개", level, null, null);
        ReflectionTestUtils.setField(course, "courseId", id);
        return course;
    }

    private static Course approvedCourse(Long id, Long instructorId) {
        Course course = course(id, instructorId, 21L, CourseLevel.BEGINNER);
        course.requestReview(NOW.minusDays(2));
        course.approve(NOW.minusDays(1));
        return course;
    }

    private static CourseRepository.CourseLessonStat stat(Long courseId, long lessons, long seconds) {
        return new CourseRepository.CourseLessonStat() {
            public Long getCourseId() { return courseId; }
            public long getLessons() { return lessons; }
            public long getTotalDurationSeconds() { return seconds; }
        };
    }
}
