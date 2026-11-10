package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CourseEditDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseSaveDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseStatusDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonIdsDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonSaveDTO;
import com.wanted.moneybeetle.domain.course.model.dto.MyCourseItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.MyCourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ThumbnailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.VideoUploadDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.inquiry.model.service.InstructorInquiryService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizCountDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.InstructorQuizService;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.file.FileStorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 생성·수정(허가 후 수정 시 재검수), 영상·요약글 저장, 검수 요청 조건 확인 (DP-34)
*   - 내 강의 목록·생성·편집 정보·수정·삭제·썸네일 (API 72~77, #30)
*   - 검수 요청 (API 81, #32)
*   - 영상 파일 올리기·영상·요약글 저장, 테스트 퀴즈·퀴즈 저장 (API 78~80, #31)
*   - 모든 작업은 본인 강의만 (남의 강의 403, 삭제된 강의 404)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorCourseService {

    private static final Set<String> STATUSES = Set.of(
            Course.STATUS_DRAFT, Course.STATUS_REVIEW, Course.STATUS_APPROVED, Course.STATUS_REJECTED);
    private static final int MAX_LESSONS = 3;               // 강의당 영상 1~3개 (SP-30, TCH-02)
    private static final int SUMMARY_MAX_LENGTH = 2000;     // 요약글 2,000자 (DP-31)
    private static final String VIDEO_KEY_PREFIX = "/uploads/";   // FileStorageService가 돌려주는 파일 키
    private static final String VIDEO_EXT = ".mp4";

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final LessonRepository lessonRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final QuizService quizService;                          // quiz 도메인은 Service로만
    private final InstructorQuizService instructorQuizService;      // quiz 도메인 - 강사 퀴즈 저장
    private final InstructorInquiryService instructorInquiryService; // inquiry 도메인은 Service로만
    private final ForbiddenWordService forbiddenWordService;         // operation 도메인 (DBA②)
    private final FileStorageService fileStorageService;             // global 파일 저장 (SCM)
    private final Clock clock;                                       // Asia/Seoul 공통 시계

    @Value("${file.upload-dir}")
    private String uploadDir;                                        // FileStorageService와 같은 저장 폴더 (영상 키 확인용)

    /* API 72 내 강의 목록: 검수 상태 포함, 수강생 수·답변 대기 문의 수·최근 반려 사유는 강의 여러 개를 한 번에 조회 */
    public MyCourseListDTO findMyCourses(Long instructorId, String status) {
        String statusFilter = status != null && STATUSES.contains(status) ? status : null;
        List<Course> courses = courseRepository.findMyCourses(instructorId, statusFilter);
        if (courses.isEmpty()) {
            return new MyCourseListDTO(List.of());
        }

        List<Long> courseIds = courses.stream().map(Course::getCourseId).toList();
        Map<Long, Long> learners = learningProgressRepository.countLearnersByCourseIds(courseIds).stream()
                .collect(Collectors.toMap(
                        LearningProgressRepository.CourseLearnerCount::getCourseId,
                        LearningProgressRepository.CourseLearnerCount::getLearners));
        Map<Long, Long> waiting = instructorInquiryService.countWaitingByCourse(courseIds);
        Map<Long, String> rejectReasons = findLatestRejectReasons(courseIds);

        return new MyCourseListDTO(courses.stream()
                .map(course -> MyCourseItemDTO.of(course,
                        learners.getOrDefault(course.getCourseId(), 0L),
                        waiting.getOrDefault(course.getCourseId(), 0L),
                        course.getStatus().equals(Course.STATUS_REJECTED)
                                ? rejectReasons.get(course.getCourseId()) : null))
                .toList());
    }

    /* API 73 강의 생성: 사용 중인 소주제에만, 금칙어 검사, 작성 중(DRAFT)으로 시작 */
    @Transactional
    public CourseStatusDTO createCourse(Long instructorId, CourseSaveDTO request) {
        checkSelectableCategory(request.categoryId());
        checkForbiddenWords(request);
        Course course = Course.create(instructorId, request.categoryId(), request.title().strip(),
                request.description(), request.infoBaseDate(), request.infoSource());
        return CourseStatusDTO.from(courseRepository.save(course));
    }

    /* API 74 강의 편집 정보: 강의 정보 + 영상·요약글 + 테스트 퀴즈·퀴즈(정답 포함) + 최근 반려 사유 */
    public CourseEditDTO getCourseForEdit(Long instructorId, Long courseId) {
        Course course = getMyCourse(instructorId, courseId);
        String rejectReason = courseReviewRepository
                .findFirstByCourseIdAndResultOrderByCreatedAtDescCourseReviewIdDesc(courseId, CourseReview.RESULT_REJECTED)
                .map(CourseReview::getReason)
                .orElse(null);
        return CourseEditDTO.of(course, rejectReason,
                lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId),
                quizService.findQuizzesForReview(courseId));
    }

    /* API 75 강의 수정: 검수 중에는 불가, 허가된 강의를 고치면 다시 검수(REVIEW) */
    @Transactional
    public CourseStatusDTO updateCourse(Long instructorId, Long courseId, CourseSaveDTO request) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        checkSelectableCategory(request.categoryId());
        checkForbiddenWords(request);
        course.change(request.categoryId(), request.title().strip(), request.description(),
                request.infoBaseDate(), request.infoSource(), LocalDateTime.now(clock));
        return CourseStatusDTO.from(course);
    }

    /* API 76 강의 삭제: 학습 기록이 있으면 삭제 불가 (SP-32), 없으면 Soft Delete */
    @Transactional
    public void deleteCourse(Long instructorId, Long courseId) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        if (learningProgressRepository.existsByCourseId(courseId)) {
            throw new BusinessException(CourseErrorCode.COURSE_HAS_LEARNERS);
        }
        course.delete(LocalDateTime.now(clock));
    }

    /* API 77 썸네일 업로드: 이미지 2MB·jpg·png 검사는 FileStorageService가 한다 (DP-32) */
    @Transactional
    public ThumbnailDTO changeThumbnail(Long instructorId, Long courseId, MultipartFile file) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        course.checkEditable();                         // 검수 중이면 파일을 저장하기 전에 막는다 (고아 파일 방지)
        try {
            String thumbnailUrl = fileStorageService.saveImage(file);
            course.changeThumbnail(thumbnailUrl, LocalDateTime.now(clock));
            return new ThumbnailDTO(thumbnailUrl);
        } catch (IOException e) {
            throw new UncheckedIOException("썸네일 저장 실패", e);
        }
    }

    /* API 78 부속 "영상 파일 올리기": mp4 500MB 검사는 FileStorageService가 한다 (DP-33).
     * 본인 강의·수정 가능 상태를 파일 저장 전에 확인한다 (남의 강의·검수 중이면 파일이 남지 않게).
     * 영상은 최대 500MB라 저장이 오래 걸리므로 트랜잭션 없이 실행해 업로드 중에 DB 연결을 붙잡지 않는다
     * (강의 조회는 Repository 호출 한 번이라 그 자체 트랜잭션으로 충분하다) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VideoUploadDTO uploadVideo(Long instructorId, Long courseId, MultipartFile file) {
        getMyCourse(instructorId, courseId).checkEditable();
        try {
            return new VideoUploadDTO(fileStorageService.saveVideo(file));
        } catch (IOException e) {
            throw new UncheckedIOException("영상 저장 실패", e);
        }
    }

    /* API 78 영상·요약글 일괄 저장 (TCH-02·03, SP-30, DP-31).
     * lessonId가 있으면 이 강의의 기존 영상을 고치고, 없으면 새로 만든다. 요청에 없는 기존 영상은 뺀다.
     * 응답 lessonIds는 요청 순서와 같다. 같은 Idempotency-Key 재요청은 IdempotencyFilter가 처음 응답을 돌려준다 */
    @Transactional
    public LessonIdsDTO saveLessons(Long instructorId, Long courseId, LessonSaveDTO request) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        course.markContentChanged(LocalDateTime.now(clock));   // 검수 중이면 409, 허가된 강의면 다시 검수
        List<LessonSaveDTO.LessonItem> items = request.lessons();
        checkLessonItems(items);

        Map<Long, Lesson> existing = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .collect(Collectors.toMap(Lesson::getLessonId, Function.identity()));
        Set<Long> keptIds = new HashSet<>();
        for (LessonSaveDTO.LessonItem item : items) {
            if (item.lessonId() == null) {
                continue;
            }
            if (!existing.containsKey(item.lessonId())) {
                throw new BusinessException(CourseErrorCode.LESSON_NOT_FOUND);   // 남의 강의 영상 ID 포함
            }
            if (!keptIds.add(item.lessonId())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);      // 같은 영상을 두 번
            }
        }

        for (LessonSaveDTO.LessonItem item : items) {
            checkVideoKey(item, existing);
        }

        removeLessons(existing.values().stream().filter(lesson -> !keptIds.contains(lesson.getLessonId())).toList());
        moveLessons(items, existing);

        List<Long> lessonIds = new ArrayList<>();
        for (LessonSaveDTO.LessonItem item : items) {
            String summary = item.summary() == null ? null : item.summary().strip();
            if (item.lessonId() != null) {
                existing.get(item.lessonId()).change(item.title().strip(), item.videoUrl(),
                        item.durationSeconds(), summary);
                lessonIds.add(item.lessonId());
            } else {
                Lesson created = lessonRepository.save(Lesson.create(courseId, item.sortOrder(), item.title().strip(),
                        item.videoUrl(), item.durationSeconds(), summary));
                lessonIds.add(created.getLessonId());
            }
        }
        return new LessonIdsDTO(lessonIds);
    }

    /* API 79 테스트 퀴즈 저장 (OX 5문제). 문항 저장은 quiz 도메인이 한다 */
    @Transactional
    public QuizCountDTO saveTestQuiz(Long instructorId, Long courseId, QuizSaveDTO request) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        course.markContentChanged(LocalDateTime.now(clock));
        return instructorQuizService.saveTestQuiz(courseId, request);
    }

    /* API 80 퀴즈 저장 (4지선다 5~15문제) */
    @Transactional
    public QuizCountDTO saveQuiz(Long instructorId, Long courseId, QuizSaveDTO request) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        course.markContentChanged(LocalDateTime.now(clock));
        return instructorQuizService.saveMainQuiz(courseId, request);
    }

    /* API 81 검수 요청: 본인 강의 → 작성 중·반려 상태 → 영상 1개 이상 + 테스트 퀴즈 5 + 퀴즈 5~15 (DP-34) */
    @Transactional
    public CourseStatusDTO requestReview(Long instructorId, Long courseId) {
        Course course = getMyCourseForUpdate(instructorId, courseId);
        course.checkRequestable();                      // 상태 오류(409)를 콘텐츠 오류보다 먼저
        if (lessonRepository.countByCourseId(courseId) < 1) {
            throw new BusinessException(CourseErrorCode.NO_LESSON);
        }
        quizService.checkReadyForReview(courseId);
        course.requestReview(LocalDateTime.now(clock));
        return CourseStatusDTO.from(course);
    }

    /* ===== 내부 도우미 ===== */

    /* 본인 강의만 (삭제된 강의는 404, 남의 강의는 403) */
    private Course getMyCourse(Long instructorId, Long courseId) {
        return checkMine(courseRepository.findById(courseId), instructorId);
    }

    /* 수정·삭제·썸네일·검수 요청용: 행 잠금으로 읽어서 검사와 저장 사이에 상태가 바뀌지 않게 한다.
     * 검수 요청·허가가 먼저 진행 중이면 끝날 때까지 기다린 뒤 바뀐 상태로 검사 → COURSE_IN_REVIEW.
     * 썸네일은 잠금을 잡은 채 상태를 확인한 뒤 파일을 저장하므로, 충돌 롤백으로 파일만 남는 일이 없다 */
    private Course getMyCourseForUpdate(Long instructorId, Long courseId) {
        return checkMine(courseRepository.findByIdForUpdate(courseId), instructorId);
    }

    private Course checkMine(Optional<Course> found, Long instructorId) {
        Course course = found
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!course.isOwnedBy(instructorId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return course;
    }

    /* 강의는 사용 중인 소주제(2단계)에만 만든다 (TCH-01) */
    private void checkSelectableCategory(Long categoryId) {
        boolean selectable = categoryRepository.findById(categoryId)
                .filter(Category::isActive)
                .filter(Category::isSubCategory)
                .isPresent();
        if (!selectable) {
            throw new BusinessException(CourseErrorCode.CATEGORY_NOT_SELECTABLE);
        }
    }

    /* 영상 요청 검사: 개수 1~3, 순서는 1부터 빠짐없이 한 번씩, 요약글 2,000자, 금칙어 (영상 키는 checkVideoKey) */
    private void checkLessonItems(List<LessonSaveDTO.LessonItem> items) {
        if (items.isEmpty()) {
            throw new BusinessException(CourseErrorCode.NO_LESSON);
        }
        if (items.size() > MAX_LESSONS) {
            throw new BusinessException(CourseErrorCode.TOO_MANY_LESSONS);
        }
        Set<Integer> orders = new HashSet<>();
        for (LessonSaveDTO.LessonItem item : items) {
            if (item.sortOrder() < 1 || item.sortOrder() > items.size() || !orders.add(item.sortOrder())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            if (item.summary() != null && item.summary().strip().length() > SUMMARY_MAX_LENGTH) {
                throw new BusinessException(CourseErrorCode.SUMMARY_TOO_LONG);
            }
            forbiddenWordService.check(item.title());
            forbiddenWordService.check(item.summary());
        }
    }

    /* 영상 키 확인: 기존 영상이 지금 저장된 키를 그대로 쓰면 통과(재업로드 없이 제목·요약글만 고칠 때).
     * 새 키는 "영상 파일 올리기"로 받은 "/uploads/....mp4" 형식이고, 저장 폴더에 실제 파일이 있어야 한다 */
    private void checkVideoKey(LessonSaveDTO.LessonItem item, Map<Long, Lesson> existing) {
        String videoUrl = item.videoUrl();
        if (item.lessonId() != null && videoUrl.equals(existing.get(item.lessonId()).getVideoUrl())) {
            return;
        }
        if (!isUploadedVideo(videoUrl)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    /* "/uploads/<파일 이름>.mp4" 이고, 저장 폴더 안(경로 이동 없음)에 그 파일이 실제로 있는지 */
    private boolean isUploadedVideo(String videoUrl) {
        if (!videoUrl.startsWith(VIDEO_KEY_PREFIX) || !videoUrl.endsWith(VIDEO_EXT)) {
            return false;
        }
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = dir.resolve(videoUrl.substring(VIDEO_KEY_PREFIX.length())).normalize();
        return file.startsWith(dir) && Files.isRegularFile(file);
    }

    /* 요청에 없는 영상 빼기. 시청 기록·마지막 본 영상이 가리키면 지울 수 없다 (FK) */
    private void removeLessons(List<Lesson> removed) {
        if (removed.isEmpty()) {
            return;
        }
        if (lessonRepository.countUsages(removed.stream().map(Lesson::getLessonId).toList()) > 0) {
            throw new BusinessException(CourseErrorCode.LESSON_IN_USE);
        }
        lessonRepository.deleteAllInBatch(removed);
    }

    /* 남는 영상의 순서를 요청대로 옮긴다. (강의, 순서)가 유일하고 순서는 1~3만 되므로
     * 비어 있는 자리로 하나씩 옮기고 바로 저장한다. 서로 자리를 바꿔야 하는데 빈 자리가 없으면(영상 3개) 막는다 */
    private void moveLessons(List<LessonSaveDTO.LessonItem> items, Map<Long, Lesson> existing) {
        Map<Lesson, Integer> pending = new HashMap<>();
        Set<Integer> occupied = new HashSet<>();
        for (LessonSaveDTO.LessonItem item : items) {
            if (item.lessonId() == null) {
                continue;
            }
            Lesson lesson = existing.get(item.lessonId());
            occupied.add(lesson.getSortOrder());
            if (lesson.getSortOrder() != item.sortOrder()) {
                pending.put(lesson, item.sortOrder());
            }
        }
        while (!pending.isEmpty()) {
            Map.Entry<Lesson, Integer> movable = pending.entrySet().stream()
                    .filter(entry -> !occupied.contains(entry.getValue()))
                    .findFirst()
                    .orElse(null);
            Lesson lesson;
            int target;
            if (movable != null) {
                lesson = movable.getKey();
                target = movable.getValue();
                pending.remove(lesson);
            } else {
                // 서로 자리를 바꾸는 경우: 한 영상을 빈 자리로 잠시 옮겨 둔다
                lesson = pending.keySet().iterator().next();
                target = freeSlot(occupied);
            }
            occupied.remove(lesson.getSortOrder());
            lesson.changeOrder(target);
            lessonRepository.saveAndFlush(lesson);
            occupied.add(target);
        }
    }

    private int freeSlot(Set<Integer> occupied) {
        for (int slot = 1; slot <= MAX_LESSONS; slot++) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }
        throw new BusinessException(CourseErrorCode.LESSON_ORDER_LOCKED);
    }

    /* 강의 글 금칙어 검사 (역할분담 §7: 문의·닉네임·강의 글) */
    private void checkForbiddenWords(CourseSaveDTO request) {
        forbiddenWordService.check(request.title());
        forbiddenWordService.check(request.description());
        forbiddenWordService.check(request.infoSource());
    }

    /* 강의별 가장 최근 반려 사유 (최신순으로 받은 뒤 강의별 첫 건만) */
    private Map<Long, String> findLatestRejectReasons(List<Long> courseIds) {
        Map<Long, String> reasons = new HashMap<>();
        courseReviewRepository.findByCourseIdInAndResultOrderByCreatedAtDescCourseReviewIdDesc(
                        courseIds, CourseReview.RESULT_REJECTED)
                .forEach(review -> reasons.putIfAbsent(review.getCourseId(), review.getReason()));
        return reasons;
    }
}
