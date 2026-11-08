package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonWatchRepository;
import com.wanted.moneybeetle.domain.course.model.dao.ScrapRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CompletedCourseDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonPlayDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ProgressDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ScrapListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import com.wanted.moneybeetle.domain.course.model.entity.Scrap;
import com.wanted.moneybeetle.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private final LearningProgressRepository learningProgressRepository;
    private final LessonWatchRepository lessonWatchRepository;
    private final ScrapRepository scrapRepository;

    /* ===== 카테고리·강의 조회 (#16) ===== */

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
    public Page<CourseListDTO> findCourses(Long memberId, Long categoryId, Pageable pageable) {
        List<Long> categoryIds = findCategoryIdsWithChildren(categoryId);

        Page<Course> courses = courseRepository.findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(
                categoryIds, Course.STATUS_APPROVED, pageable);

        // 이 페이지 강의 중 내가 스크랩한 것을 한 번에 조회 (강의마다 조회하지 않음)
        Set<Long> scrappedIds = (memberId == null)
                ? Set.of()   // 회원을 모르면 스크랩 안 한 것으로
                : findScrappedCourseIds(memberId, courses.getContent().stream().map(Course::getCourseId).toList());

        return courses.map(course -> CourseListDTO.from(
                course,
                null,   // TODO 강사 이름: DBA ② 메서드가 생기면 연결
                (int) lessonRepository.countByCourseId(course.getCourseId()),
                null,   // TODO 내 학습 상태
                scrappedIds.contains(course.getCourseId())
        ));
    }

    /* API 31. 강의 상세 (LRN-02, LRN-04, SP-41) */
    public CourseDetailDTO getCourseDetail(Long memberId, Long courseId) {
        Course course = getVisibleCourse(courseId);
        Category category = categoryRepository.findById(course.getCategoryId()).orElse(null);
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        boolean scrapped = memberId != null && scrapRepository.existsByMemberIdAndCourseId(memberId, courseId);

        return CourseDetailDTO.from(course, category, lessons, scrapped);
    }

    /* ===== 영상 재생·이어보기·시청 기록 (#17) ===== */

    /* API 32. 영상 재생 정보 + 이어보기 위치 (LRN-03, LRN-05) */
    public LessonPlayDTO getLessonPlay(Long memberId, Long courseId, Long lessonId) {
        getVisibleCourse(courseId);
        Lesson lesson = getLessonOfCourse(courseId, lessonId);

        int positionSeconds = learningProgressRepository.findByMemberIdAndCourseId(memberId, courseId)
                .map(progress -> progress.positionOf(lessonId))
                .orElse(0);

        return LessonPlayDTO.from(lesson, positionSeconds);
    }

    /* API 33. 시청 위치 저장 (LRN-05, LRN-06, SP-38) */
    @Transactional
    public void saveProgress(Long memberId, Long courseId, Long lessonId, ProgressDTO request) {
        getVisibleCourse(courseId);
        Lesson lesson = getLessonOfCourse(courseId, lessonId);

        LearningProgress progress = learningProgressRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseGet(() -> learningProgressRepository.save(LearningProgress.start(memberId, courseId)));
        progress.moveTo(lessonId, lesson.clampSeconds(request.getPositionSeconds()));

        LessonWatch watch = lessonWatchRepository.findByMemberIdAndLessonId(memberId, lessonId)
                .orElseGet(() -> lessonWatchRepository.save(LessonWatch.start(memberId, lessonId)));
        watch.record(lesson.clampSeconds(request.getWatchedSeconds()), request.isCompleted());
        // 수정은 save()를 다시 부르지 않는다. 트랜잭션이 끝날 때 변경 감지로 UPDATE된다
    }

    /* API 34. 시청 기록: 최근 본 영상 순 (LRN-06) */
    public Page<WatchHistoryDTO> findWatchHistories(Long memberId, Pageable pageable) {
        Page<LessonWatch> watches = lessonWatchRepository.findVisibleByMemberId(memberId, Course.STATUS_APPROVED, pageable);

        Map<Long, Lesson> lessons = lessonRepository.findAllById(
                        watches.getContent().stream().map(LessonWatch::getLessonId).toList())
                .stream()
                .collect(Collectors.toMap(Lesson::getLessonId, Function.identity()));
        Map<Long, Course> courses = findCoursesByIds(
                lessons.values().stream().map(Lesson::getCourseId).toList());

        return watches.map(watch -> {
            Lesson lesson = lessons.get(watch.getLessonId());
            return WatchHistoryDTO.from(watch, lesson, courses.get(lesson.getCourseId()));
        });
    }

    /* API 35. 학습 완료 목록 (LRN-06, LRN-07) */
    public Page<CompletedCourseDTO> findCompletedCourses(Long memberId, Pageable pageable) {
        Page<LearningProgress> completed = learningProgressRepository.findVisibleByMemberIdAndStatus(
                memberId, LearningProgress.STATUS_COMPLETED, Course.STATUS_APPROVED, pageable);

        Map<Long, Course> courses = findCoursesByIds(
                completed.getContent().stream().map(LearningProgress::getCourseId).toList());

        return completed.map(progress -> CompletedCourseDTO.from(progress, courses.get(progress.getCourseId())));
    }

    /* ===== 스크랩 (#18) ===== */

    /* API 36. 스크랩 (LRN-08, SP-39: 같은 강의는 한 번만) */
    @Transactional
    public void createScrap(Long memberId, Long courseId) {
        getVisibleCourse(courseId);

        if (scrapRepository.existsByMemberIdAndCourseId(memberId, courseId)) {
            throw new BusinessException(CourseErrorCode.ALREADY_SCRAPPED);
        }
        try {
            // saveAndFlush: INSERT를 바로 실행해서 중복 위반을 이 메서드 안에서 잡는다
            scrapRepository.saveAndFlush(Scrap.create(memberId, courseId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 같은 요청이 먼저 저장된 경우 (UNIQUE(member_id, course_id) 위반) → 500이 아니라 409
            throw new BusinessException(CourseErrorCode.ALREADY_SCRAPPED);
        }
    }

    /* API 37. 스크랩 해제 (실제 삭제). 강의가 비공개로 바뀌었어도 해제는 할 수 있어야 해서 공개 검사를 하지 않는다 */
    @Transactional
    public void deleteScrap(Long memberId, Long courseId) {
        scrapRepository.deleteByMemberIdAndCourseId(memberId, courseId);
    }

    /* API 38. 내 스크랩 목록: 최근 스크랩 순 (LRN-08, USR-11) */
    public Page<ScrapListDTO> findScraps(Long memberId, Pageable pageable) {
        Page<Scrap> scraps = scrapRepository.findByMemberIdOrderByCreatedAtDescScrapIdDesc(memberId, pageable);

        Map<Long, Course> courses = findCoursesByIds(
                scraps.getContent().stream().map(Scrap::getCourseId).toList());
        Map<Long, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

        return scraps.map(scrap -> {
            Course course = courses.get(scrap.getCourseId());
            return ScrapListDTO.from(scrap, course, categoryPath(course.getCategoryId(), categories));
        });
    }

    /* ===== 다른 담당이 부르는 메서드 (역할분담 7장) ===== */

    /* 김예린: 로그인 후 홈 vr1/vr2 분기 */
    public boolean hasLearningHistory(Long memberId) {
        return learningProgressRepository.existsByMemberId(memberId);
    }

    /* 김예린: 홈 '이어서 볼 강의' (최근에 본 순서, 최대 5개) */
    public List<CourseListDTO> findContinueWatching(Long memberId) {
        List<LearningProgress> progresses = learningProgressRepository
                .findTop5ByMemberIdAndStatusOrderByUpdatedAtDesc(memberId, LearningProgress.STATUS_IN_PROGRESS);
        Map<Long, Course> courses = findCoursesByIds(
                progresses.stream().map(LearningProgress::getCourseId).toList());

        return progresses.stream()
                .map(progress -> courses.get(progress.getCourseId()))
                .filter(course -> course != null && course.isVisibleToLearner())
                .map(course -> CourseListDTO.from(
                        course,
                        null,   // TODO 강사 이름
                        (int) lessonRepository.countByCourseId(course.getCourseId()),
                        LearningProgress.STATUS_IN_PROGRESS,
                        false))
                .toList();
    }

    /* 이재성: 통합 검색 */
    public List<CourseListDTO> search(String keyword) {
        return List.of(); // TODO 제목 검색 (허가 + 공개만)
    }

    /* ===== 내부 도우미 ===== */

    /* 학습자에게 보여도 되는 강의만 꺼낸다. 없는 강의와 비공개 강의는 같은 에러 (SP-31) */
    private Course getVisibleCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .filter(Course::isVisibleToLearner)
                .orElseThrow(() -> new BusinessException(CourseErrorCode.COURSE_NOT_PUBLIC)); // TODO 공통 설정 병합 후 BusinessException으로
    }

    /* 그 강의에 속한 영상인지 확인한다 */
    private Lesson getLessonOfCourse(Long courseId, Long lessonId) {
        return lessonRepository.findByLessonIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorCode.LESSON_NOT_FOUND)); // TODO 공통 설정 병합 후 BusinessException으로
    }

    private Map<Long, Course> findCoursesByIds(List<Long> courseIds) {
        return courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getCourseId, Function.identity()));
    }

    private Set<Long> findScrappedCourseIds(Long memberId, List<Long> courseIds) {
        return scrapRepository.findByMemberIdAndCourseIdIn(memberId, courseIds).stream()
                .map(Scrap::getCourseId)
                .collect(Collectors.toSet());
    }

    /* "부동산 › 청약"처럼 상위 › 소주제 이름을 만든다 */
    private String categoryPath(Long categoryId, Map<Long, Category> categories) {
        Category category = categories.get(categoryId);
        if (category == null) {
            return null;
        }
        Category parent = category.isSubCategory() ? categories.get(category.getParentId()) : null;
        return parent != null ? parent.getName() + " › " + category.getName() : category.getName();
    }

    /* 상위 카테고리를 고르면 그 아래 소주제 강의까지 함께 보여 준다 */
    private List<Long> findCategoryIdsWithChildren(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        categoryRepository.findByParentIdAndIsActiveTrue(categoryId)
                .forEach(child -> ids.add(child.getCategoryId()));
        return ids;
    }
}