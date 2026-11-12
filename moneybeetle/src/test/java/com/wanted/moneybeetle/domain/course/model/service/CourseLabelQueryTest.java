package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonWatchRepository;
import com.wanted.moneybeetle.domain.course.model.dao.ScrapRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.member.model.service.InstructorService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#296)
*   강의 이름표 조회(오답노트용 findVisibleCourseLabels)와 통합 검색(search)
*   - courseId 가 있으면 그 강의 1건만 ID 로 조회한다 (공개 강의 전체를 읽고 메모리에서 거르지 않는다)
*   - 통합 검색은 최대 50개만 가져온다
*   - 카테고리 이름은 CategoryPathService(캐시)에서 받는다
*  */
class CourseLabelQueryTest {

    private static final Long COURSE = 15L;
    private static final Long REAL_ESTATE = 1L;      // 상위
    private static final Long SUBSCRIPTION = 11L;    // 부동산 › 청약
    private static final Long CAPITAL = 2L;          // 다른 상위

    private CategoryRepository categoryRepository;
    private CourseRepository courseRepository;
    private CategoryPathService categoryPathService;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        courseRepository = mock(CourseRepository.class);
        categoryPathService = mock(CategoryPathService.class);
        courseService = new CourseService(categoryRepository, courseRepository, mock(LessonRepository.class),
                mock(LearningProgressRepository.class), mock(LessonWatchRepository.class), mock(ScrapRepository.class),
                mock(CourseReviewRepository.class),
                Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(InstructorService.class), categoryPathService);
        when(categoryPathService.findCategoryPaths())
                .thenReturn(Map.of(REAL_ESTATE, "부동산", SUBSCRIPTION, "부동산 › 청약", CAPITAL, "자본"));
        Category sub = Category.createCategory("청약", 1);
        ReflectionTestUtils.setField(sub, "categoryId", SUBSCRIPTION);
        when(categoryRepository.findByParentIdAndIsActiveTrue(REAL_ESTATE)).thenReturn(List.of(sub));
    }

    private static Course course(Long id, Long categoryId, boolean visible) {
        Course course = Course.create(2L, categoryId, "청약통장 기초", "소개", null, null);
        ReflectionTestUtils.setField(course, "courseId", id);
        ReflectionTestUtils.setField(course, "status", CourseStatus.APPROVED);
        ReflectionTestUtils.setField(course, "isPublic", visible);
        return course;
    }

    @Test
    @DisplayName("강의 ID 가 있으면 그 강의 1건만 ID 로 조회하고, 공개 강의 전체 목록은 읽지 않는다")
    void singleCourseById() {
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(COURSE, SUBSCRIPTION, true)));

        List<CourseLabelDTO> labels = courseService.findVisibleCourseLabels(null, COURSE);

        assertThat(labels).extracting(CourseLabelDTO::getCourseId).containsExactly(COURSE);
        assertThat(labels.get(0).getCategoryName()).isEqualTo("부동산 › 청약");
        verify(courseRepository, never()).findByStatusAndIsPublicTrueAndDeletedAtIsNull(any());
        verify(courseRepository, never()).findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(anyList(), any());
        verifyNoInteractions(categoryRepository);   // 카테고리를 고르지 않았으면 하위 소주제도 찾지 않는다
    }

    @Test
    @DisplayName("강의 ID 로 찾은 강의가 비공개면 빈 목록이다")
    void hiddenCourseById() {
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(COURSE, SUBSCRIPTION, false)));

        assertThat(courseService.findVisibleCourseLabels(null, COURSE)).isEmpty();
        verifyNoInteractions(categoryPathService);
    }

    @Test
    @DisplayName("강의 ID 와 상위 카테고리를 같이 주면, 그 강의가 하위 소주제에 있어도 나온다")
    void courseIdWithParentCategory() {
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(COURSE, SUBSCRIPTION, true)));

        assertThat(courseService.findVisibleCourseLabels(REAL_ESTATE, COURSE))
                .extracting(CourseLabelDTO::getCourseId).containsExactly(COURSE);
    }

    @Test
    @DisplayName("강의 ID 와 다른 카테고리를 같이 주면 빈 목록이다")
    void courseIdWithOtherCategory() {
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(COURSE, SUBSCRIPTION, true)));

        assertThat(courseService.findVisibleCourseLabels(CAPITAL, COURSE)).isEmpty();
    }

    @Test
    @DisplayName("통합 검색은 최근 강의부터 50개까지만 요청한다")
    void searchLimitedTo50() {
        when(courseRepository.findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
                "청약", CourseStatus.APPROVED, Limit.of(50)))
                .thenReturn(List.of(course(COURSE, SUBSCRIPTION, true)));

        List<CourseLabelDTO> result = courseService.search("청약");

        assertThat(result).extracting(CourseLabelDTO::getCategoryName).containsExactly("부동산 › 청약");
        verify(courseRepository).findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
                "청약", CourseStatus.APPROVED, Limit.of(50));
    }

    @Test
    @DisplayName("검색 결과가 없으면 카테고리 이름도 읽지 않는다")
    void emptySearchSkipsCategories() {
        when(courseRepository.findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
                anyString(), any(), any(Limit.class))).thenReturn(List.of());

        assertThat(courseService.search("없는말")).isEmpty();
        verifyNoInteractions(categoryPathService);
    }
}
