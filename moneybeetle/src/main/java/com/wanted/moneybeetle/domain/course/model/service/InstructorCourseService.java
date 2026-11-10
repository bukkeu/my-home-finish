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
import com.wanted.moneybeetle.domain.course.model.dto.MyCourseItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.MyCourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.ThumbnailDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Category;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import com.wanted.moneybeetle.domain.inquiry.model.service.InstructorInquiryService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.file.FileStorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 생성·수정(허가 후 수정 시 재검수), 영상·요약글 저장, 검수 요청 조건 확인 (DP-34)
*   - 내 강의 목록·생성·편집 정보·수정·삭제·썸네일 (API 72~77, #30)
*   - 검수 요청 (API 81, #32)
*   - 모든 작업은 본인 강의만 (남의 강의 403, 삭제된 강의 404)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorCourseService {

    private static final Set<String> STATUSES = Set.of(
            Course.STATUS_DRAFT, Course.STATUS_REVIEW, Course.STATUS_APPROVED, Course.STATUS_REJECTED);

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final LessonRepository lessonRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final QuizService quizService;                          // quiz 도메인은 Service로만
    private final InstructorInquiryService instructorInquiryService; // inquiry 도메인은 Service로만
    private final ForbiddenWordService forbiddenWordService;         // operation 도메인 (DBA②)
    private final FileStorageService fileStorageService;             // global 파일 저장 (SCM)
    private final Clock clock;                                       // Asia/Seoul 공통 시계

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
