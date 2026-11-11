package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonWatchRepository;
import com.wanted.moneybeetle.domain.course.model.dao.ScrapRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CategoryTreeDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CompletedCourseDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonPlayDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ProgressDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ScrapListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import com.wanted.moneybeetle.domain.course.model.entity.Scrap;
import com.wanted.moneybeetle.domain.member.model.service.InstructorService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final CourseReviewRepository courseReviewRepository;
    private final Clock clock;                          // Asia/Seoul 공통 시계 (학습 완료 시각)
    private final MemberService memberService;          // member 도메인 (DBA②) - 회원 행 잠금
    private final InstructorService instructorService;  // member 도메인 - 강사 이름 (#52)

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
                categoryIds, CourseStatus.APPROVED, pageable);

        // 이 페이지 강의 중 내가 스크랩한 것을 한 번에 조회 (강의마다 조회하지 않음)
        Set<Long> scrappedIds = (memberId == null)
                ? Set.of()   // 회원을 모르면 스크랩 안 한 것으로
                : findScrappedCourseIds(memberId, courses.getContent().stream().map(Course::getCourseId).toList());

        List<Long> courseIds = courses.getContent().stream().map(Course::getCourseId).toList();
        Map<Long, Long> lessonCounts = countLessons(courseIds);
        Map<Long, String> instructorNames = instructorService.findDisplayNames(
                courses.getContent().stream().map(Course::getInstructorId).distinct().toList());
        // 내 학습 상태·끝까지 본 영상 수도 강의마다 조회하지 않고 한 번에 (#52)
        Map<Long, String> statuses = findProgressStatuses(memberId, courseIds);
        Map<Long, Long> watchedCounts = countWatchedLessons(memberId, courseIds);

        return courses.map(course -> CourseListDTO.from(
                course,
                instructorNames.get(course.getInstructorId()),
                lessonCounts.getOrDefault(course.getCourseId(), 0L).intValue(),
                watchedCounts.getOrDefault(course.getCourseId(), 0L).intValue(),
                statuses.get(course.getCourseId()),
                scrappedIds.contains(course.getCourseId())
        ));
    }

    /* API 31. 강의 상세 (LRN-02, LRN-04, SP-41) */
    public CourseDetailDTO getCourseDetail(Long memberId, Long courseId) {
        Course course = getVisibleCourse(courseId);
        Category category = categoryRepository.findById(course.getCategoryId()).orElse(null);
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        boolean scrapped = memberId != null && scrapRepository.existsByMemberIdAndCourseId(memberId, courseId);
        String instructorName = instructorService.findDisplayNames(List.of(course.getInstructorId()))
                .get(course.getInstructorId());

        // 내 진행 상태·끝까지 본 영상 (#52, 목차 완료 표시·이어보기). 비로그인이면 없음
        LearningProgress progress = memberId == null ? null
                : learningProgressRepository.findByMemberIdAndCourseId(memberId, courseId).orElse(null);
        Set<Long> completedLessonIds = memberId == null || lessons.isEmpty() ? Set.of()
                : lessonWatchRepository.findByMemberIdAndLessonIdInAndIsCompletedTrue(memberId,
                                lessons.stream().map(Lesson::getLessonId).toList()).stream()
                        .map(LessonWatch::getLessonId)
                        .collect(Collectors.toSet());

        return CourseDetailDTO.from(course, category, instructorName, lessons, completedLessonIds, scrapped,
                progress == null ? null : progress.getStatus(),
                progress == null ? null : progress.getLastLessonId());
    }

    /* ===== 영상 재생·이어보기·시청 기록 (#17) ===== */

    /* API 32. 영상 재생 정보 + 이어보기 위치 (LRN-03, LRN-05).
     * 위치는 영상마다 저장한 값(lesson_watch.position_seconds, #71)을 쓴다 → 같은 강의의 다른 영상을 본 뒤에도
     * 마이페이지 이어보기로 이 영상을 열면 보던 위치부터 재생된다.
     * 시청 기록이 아직 없으면(영상별 위치를 쓰기 전 데이터) 강의의 마지막 영상일 때만 진행 기록의 위치를 쓴다.
     * 시청 기록이 있으면 0초도 그대로 쓴다 (처음으로 되감은 것도 유효한 위치) */
    public LessonPlayDTO getLessonPlay(Long memberId, Long courseId, Long lessonId) {
        getVisibleCourse(courseId);
        Lesson lesson = getLessonOfCourse(courseId, lessonId);

        Optional<LessonWatch> watch = lessonWatchRepository.findByMemberIdAndLessonId(memberId, lessonId);
        int positionSeconds = watch.map(LessonWatch::getPositionSeconds)
                .orElseGet(() -> learningProgressRepository.findByMemberIdAndCourseId(memberId, courseId)
                        .filter(progress -> lessonId.equals(progress.getLastLessonId()))
                        .map(LearningProgress::getLastPositionSeconds)
                        .orElse(0));
        int watchedSeconds = watch.map(LessonWatch::getWatchedSeconds).orElse(0);

        return LessonPlayDTO.from(lesson, positionSeconds, watchedSeconds);
    }

    /* API 33. 시청 위치 저장 (LRN-05, LRN-06, SP-38).
     * 퀴즈 제출(QuizService.submitQuiz)과 같은 회원 행 잠금을 먼저 잡아, 학습 완료 상태를 오래된 값으로 덮어쓰지 않는다.
     * 영상 완료 여부는 화면의 completed 값이 아니라 서버 시계로 확인한 본 시간으로 정한다 (LessonWatch.record) */
    @Transactional
    public void saveProgress(Long memberId, Long courseId, Long lessonId, ProgressDTO request) {
        memberService.lockBalance(memberId);
        getVisibleCourse(courseId);
        Lesson lesson = getLessonOfCourse(courseId, lessonId);

        int positionSeconds = lesson.clampSeconds(request.getPositionSeconds());
        // 강의의 마지막 영상(처음 열 영상)은 진행 기록에, 이어보기 위치는 영상별 시청 기록에 남긴다 (#71)
        LearningProgress progress = learningProgressRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseGet(() -> learningProgressRepository.save(LearningProgress.start(memberId, courseId)));
        progress.moveTo(lessonId, positionSeconds);

        LocalDateTime now = LocalDateTime.now(clock);
        LessonWatch watch = lessonWatchRepository.findByMemberIdAndLessonId(memberId, lessonId)
                .orElseGet(() -> lessonWatchRepository.save(LessonWatch.start(memberId, lessonId, now)));
        watch.record(lesson.clampSeconds(request.getWatchedSeconds()), lesson.getDurationSeconds(), now);
        watch.moveTo(positionSeconds);
        // 수정은 save()를 다시 부르지 않는다. 트랜잭션이 끝날 때 변경 감지로 UPDATE된다
    }

    /* API 34. 시청 기록: 최근 본 영상 순 (LRN-06) */
    public Page<WatchHistoryDTO> findWatchHistories(Long memberId, Pageable pageable) {
        Page<LessonWatch> watches = lessonWatchRepository.findVisibleByMemberId(memberId, CourseStatus.APPROVED, pageable);

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
                memberId, LearningProgress.STATUS_COMPLETED, CourseStatus.APPROVED, pageable);

        Map<Long, Course> courses = findCoursesByIds(
                completed.getContent().stream().map(LearningProgress::getCourseId).toList());

        return completed.map(progress -> CompletedCourseDTO.from(progress, courses.get(progress.getCourseId())));
    }

    /* ===== 홈·내 정보용 개수·마지막 시청 기록 (#188, 호출부 변경은 #267) ===== */

    /* 본 영상 수: 시청 기록(API 34, findWatchHistories)과 같은 조건(이 회원, 허가 + 공개 + 삭제 안 된 강의)으로 개수만 센다 */
    public long countWatchedLessons(Long memberId) {
        return lessonWatchRepository.countVisibleByMemberId(memberId, CourseStatus.APPROVED);
    }

    /* 학습 완료한 강의 수: 학습 완료 목록(API 35, findCompletedCourses)과 같은 조건으로 개수만 센다 */
    public long countCompletedCourses(Long memberId) {
        return learningProgressRepository.countVisibleByMemberIdAndStatus(
                memberId, LearningProgress.STATUS_COMPLETED, CourseStatus.APPROVED);
    }

    /* 이 강의에서 가장 최근에 본 영상 기록 한 건 (홈 '이어서 볼 강의').
     * 시청 기록(API 34)과 같은 조건·순서라 "최근 시청 목록에서 이 강의의 첫 항목"과 같다. 기록이 아무리 많아도 DB에서 바로 찾는다.
     * 본 적 없거나 강의가 보이지 않으면(비공개·삭제) 빈 값 */
    public Optional<WatchHistoryDTO> findLastWatched(Long memberId, Long courseId) {
        return lessonWatchRepository.findLatestVisibleByMemberIdAndCourseId(
                        memberId, courseId, CourseStatus.APPROVED, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .flatMap(watch -> lessonRepository.findById(watch.getLessonId())
                        .flatMap(lesson -> courseRepository.findById(lesson.getCourseId())
                                .map(course -> WatchHistoryDTO.from(watch, lesson, course))));
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

    /* API 38. 내 스크랩 목록: 최근 스크랩 순 (LRN-08, USR-11). 재검수·비공개·삭제된 강의는 숨긴다 (SP-31) */
    public Page<ScrapListDTO> findScraps(Long memberId, Pageable pageable) {
        Page<Scrap> scraps = scrapRepository.findVisibleScraps(memberId, pageable);

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
        List<Long> courseIds = progresses.stream().map(LearningProgress::getCourseId).toList();
        Map<Long, Course> courses = findCoursesByIds(courseIds);
        Map<Long, Long> lessonCounts = countLessons(courseIds);
        Map<Long, Long> watchedCounts = countWatchedLessons(memberId, courseIds);
        Map<Long, String> instructorNames = instructorService.findDisplayNames(
                courses.values().stream().map(Course::getInstructorId).distinct().toList());

        return progresses.stream()
                .map(progress -> courses.get(progress.getCourseId()))
                .filter(course -> course != null && course.isVisibleToLearner())
                .map(course -> CourseListDTO.from(
                        course,
                        instructorNames.get(course.getInstructorId()),
                        lessonCounts.getOrDefault(course.getCourseId(), 0L).intValue(),
                        watchedCounts.getOrDefault(course.getCourseId(), 0L).intValue(),
                        LearningProgress.STATUS_IN_PROGRESS,
                        false))
                .toList();
    }

    /* 퀴즈(quiz 도메인): 강의가 공개 상태인지 확인. 아니면 COURSE_NOT_PUBLIC */
    public void checkCourseVisible(Long courseId) {
        getVisibleCourse(courseId);
    }

    /* 퀴즈(quiz 도메인) 오답노트: 보이는 강의(허가+공개+미삭제)의 이름표.
     * categoryId(하위 소주제 포함)·courseId로 좁힐 수 있고, 둘 다 null이면 보이는 강의 전체 */
    public List<CourseLabelDTO> findVisibleCourseLabels(Long categoryId, Long courseId) {
        List<Course> courses = (categoryId == null)
                ? courseRepository.findByStatusAndIsPublicTrueAndDeletedAtIsNull(CourseStatus.APPROVED)
                : courseRepository.findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(
                findCategoryIdsWithChildren(categoryId), CourseStatus.APPROVED);

        Map<Long, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

        return courses.stream()
                .filter(course -> courseId == null || course.getCourseId().equals(courseId))
                .map(course -> CourseLabelDTO.of(course.getCourseId(), course.getTitle(),
                        categoryPath(course.getCategoryId(), categories)))
                .toList();
    }

    /* 문의(inquiry 도메인): 이 강의의 강사 회원 ID. 강의가 없거나 삭제됐으면 null
     * 비공개 강의라도 강사는 자기 강의 문의를 봐야 해서 공개 여부는 보지 않는다.
     * 삭제 여부는 findCourseIdsByInstructor와 같은 기준(deletedAt is null)으로 거른다 */
    public Long findInstructorId(Long courseId) {
        return courseRepository.findById(courseId)
                .filter(course -> course.getDeletedAt() == null)
                .map(Course::getInstructorId)
                .orElse(null);
    }

    /* 문의(inquiry 도메인): 이 강사가 맡은 강의 ID 목록 (문의 목록에서 '내 강의 글' 거르기) */
    public List<Long> findCourseIdsByInstructor(Long instructorId) {
        return courseRepository.findByInstructorIdAndDeletedAtIsNull(instructorId).stream()
                .map(Course::getCourseId)
                .toList();
    }

    /* 문의(inquiry 도메인) 강사 문의함(API 82): 이 강사가 맡은 강의(삭제 제외)의 ID → 제목 */
    public Map<Long, String> findCourseTitlesByInstructor(Long instructorId) {
        return courseRepository.findByInstructorIdAndDeletedAtIsNull(instructorId).stream()
                .collect(Collectors.toMap(Course::getCourseId, Course::getTitle));
    }

    /* 문의(inquiry 도메인) 마이페이지 문의 모음(API 149, #71): 수강생에게 보이는 강의(허가+공개+미삭제)만 ID → 제목 */
    public Map<Long, String> findVisibleCourseTitles(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        return courseRepository.findAllById(courseIds).stream()
                .filter(Course::isVisibleToLearner)
                .collect(Collectors.toMap(Course::getCourseId, Course::getTitle, (first, second) -> first));
    }

    /* 퀴즈(quiz 도메인, API 41·42): 이 강의의 영상을 모두 끝까지 봤는지 (QST-02 "영상을 모두 본 뒤 퀴즈").
     * 영상이 없는 강의는 퀴즈를 열지 않는다 */
    public boolean hasWatchedAllLessons(Long memberId, Long courseId) {
        List<Long> lessonIds = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .map(Lesson::getLessonId)
                .toList();
        if (lessonIds.isEmpty()) {
            return false;
        }
        return lessonWatchRepository.countByMemberIdAndLessonIdInAndIsCompletedTrue(memberId, lessonIds)
                == lessonIds.size();
    }

    /* 퀴즈(quiz 도메인, API 42): 퀴즈 통과 → 학습 완료 (SP-34, LRN-07).
     * 이번에 처음 완료됐으면 true (보상은 이때만), 이미 완료였으면 false */
    @Transactional
    public boolean completeLearning(Long memberId, Long courseId) {
        LearningProgress progress = learningProgressRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseGet(() -> learningProgressRepository.save(LearningProgress.start(memberId, courseId)));
        return progress.complete(LocalDateTime.now(clock));
    }

    /* 이재성: 통합 검색 (#15). 보이는 강의(허가+공개+미삭제)만, 제목에 키워드가 들어간 것을 최근 강의부터 */
    public List<CourseLabelDTO> search(String keyword) {
        List<Course> courses = courseRepository
                .findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
                        keyword, CourseStatus.APPROVED);
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));
        return courses.stream()
                .map(course -> CourseLabelDTO.of(course.getCourseId(), course.getTitle(),
                        categoryPath(course.getCategoryId(), categories)))
                .toList();
    }

    /* ===== 내부 도우미 ===== */

    /* 학습자에게 보여도 되는 강의만 꺼낸다. 없는 강의와 비공개 강의는 같은 에러 (SP-31) */
    private Course getVisibleCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .filter(Course::isVisibleToLearner)
                .orElseThrow(() -> new BusinessException(CourseErrorCode.COURSE_NOT_PUBLIC));
    }

    /* 그 강의에 속한 영상인지 확인한다 */
    private Lesson getLessonOfCourse(Long courseId, Long lessonId) {
        return lessonRepository.findByLessonIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorCode.LESSON_NOT_FOUND));
    }

    private Map<Long, Course> findCoursesByIds(List<Long> courseIds) {
        return courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getCourseId, Function.identity()));
    }

    /* 강의별 영상 수를 한 번에 (강의마다 count 쿼리를 보내지 않는다, #87) */
    private Map<Long, Long> countLessons(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseRepository.countLessonsByCourseIds(courseIds).stream()
                .collect(Collectors.toMap(CourseRepository.CourseLessonCount::getCourseId,
                        CourseRepository.CourseLessonCount::getLessons));
    }

    /* 강의별 내 학습 상태 (IN_PROGRESS / COMPLETED). 비로그인이면 비어 있다 (#52) */
    private Map<Long, String> findProgressStatuses(Long memberId, List<Long> courseIds) {
        if (memberId == null || courseIds.isEmpty()) {
            return Map.of();
        }
        return learningProgressRepository.findByMemberIdAndCourseIdIn(memberId, courseIds).stream()
                .collect(Collectors.toMap(LearningProgress::getCourseId, LearningProgress::getStatus));
    }

    /* 강의별 내가 끝까지 본 영상 수 (진행률). 비로그인이면 비어 있다 (#52) */
    private Map<Long, Long> countWatchedLessons(Long memberId, List<Long> courseIds) {
        if (memberId == null || courseIds.isEmpty()) {
            return Map.of();
        }
        return lessonWatchRepository.countCompletedByCourse(memberId, courseIds).stream()
                .collect(Collectors.toMap(LessonWatchRepository.CourseWatchedCount::getCourseId,
                        LessonWatchRepository.CourseWatchedCount::getWatched));
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

    /* 대시보드(#33): 검수 대기(REVIEW) 강의 수 */
    public long countReviewWaiting() {
        return courseRepository.countByStatusAndDeletedAtIsNull(CourseStatus.REVIEW);
    }

    /* 강사 강의 목록(API 79): 가장 최근 반려 사유. 반려 이력이 없으면 null */
    public String findLatestRejectReason(Long courseId) {
        return courseReviewRepository
                .findFirstByCourseIdAndResultOrderByCreatedAtDescCourseReviewIdDesc(courseId, CourseReview.RESULT_REJECTED)
                .map(CourseReview::getReason)
                .orElse(null);
    }
}