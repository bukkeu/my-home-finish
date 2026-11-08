package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   카테고리·강의 조회(허가 + 공개만), 이어보기, 시청 기록, 스크랩
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    /* ===== 카테고리·강의 조회 (이번 이슈) ===== */

    /* API 29. 카테고리 → 소주제 트리 (LRN-01) */
    public List<CategoryTreeDTO> findCategoryTree() {
        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByDepthAscSortOrderAsc();

        Map<Long, List<Category>> childrenByParent = categories.stream()
                .filter(Category::isSubCategory)
                .collect(Collectors.groupingBy(Category::getParentId));

        return categories.stream()
                .filter(category -> !category.isSubCategory())
                .map(top -> CategoryTreeDTO.from(top,
                        childrenByParent.getOrDefault(top.getCategoryId(), List.of())))
                .toList();
    }

    /* API 30. 강의 목록: 허가 + 공개 + 삭제 안 된 강의만 (LRN-01, SP-31) */
    public Page<CourseListDTO> findCourses(Long categoryId, Pageable pageable) {
        List<Long> categoryIds = findCategoryIdsWithChildren(categoryId);

        return courseRepository
                .findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(
                        categoryIds, Course.STATUS_APPROVED, pageable)
                .map(course -> CourseListDTO.from(
                        course,
                        null,   // TODO 강사 이름: DBA ② 메서드가 생기면 연결
                        (int) lessonRepository.countByCourseId(course.getCourseId()),
                        null,   // TODO 내 학습 상태: 이어보기 이슈에서 연결
                        false   // TODO 스크랩 여부: 스크랩 이슈에서 연결
                ));
    }

    /* API 31. 강의 상세 (LRN-02, LRN-04, SP-41) */
    public CourseDetailDTO getCourseDetail(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(Course::isVisibleToLearner)
                .orElseThrow(() -> new RuntimeException(CourseErrorCode.COURSE_NOT_PUBLIC.getMessage())); // TODO 공통 설정 병합 후 BusinessException으로

        Category category = categoryRepository.findById(course.getCategoryId()).orElse(null);
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);

        return CourseDetailDTO.from(course, category, lessons);
    }

    /* 상위 카테고리(예: 돈 굴리기)를 고르면 그 아래 소주제 강의까지 함께 보여 준다 */
    private List<Long> findCategoryIdsWithChildren(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        categoryRepository.findByParentIdAndIsActiveTrue(categoryId)
                .forEach(child -> ids.add(child.getCategoryId()));
        return ids;
    }

    /* ===== 이어보기·시청 기록 (다음 이슈에서 이 아래에 추가) ===== */

    /* ===== 스크랩 (그다음 이슈에서 이 아래에 추가) ===== */

    /* ===== 다른 담당이 부르는 메서드 (역할분담 7장). 이름만 먼저 공개 ===== */

    /* DBA ②: 로그인 후 홈 vr1/vr2 분기 */
    public boolean hasLearningHistory(Long memberId) {
        return false; // TODO 이어보기 이슈에서 learning_progress로 구현
    }

    /* DBA ②: 홈 '이어서 볼 강의' */
    public List<CourseListDTO> findContinueWatching(Long memberId) {
        return List.of(); // TODO 이어보기 이슈에서 구현
    }

    /* 이재성: 통합 검색 */
    public List<CourseListDTO> search(String keyword) {
        return List.of(); // TODO 제목 검색 (허가 + 공개만)
    }
}