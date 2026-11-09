package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.AdminCourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.AdminCourseListItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseReviewResultDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// NotificationService import는 빨간 줄이 뜨면 Alt+Enter

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 89~94 관리자 강의 검수·공개 관리 (ADM-03, ADM-04)
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCourseService {

    private static final Set<String> STATUSES = Set.of(
            Course.STATUS_DRAFT, Course.STATUS_REVIEW, Course.STATUS_APPROVED, Course.STATUS_REJECTED);
    private static final String NOTIFICATION_TYPE_REJECTED = "COURSE_REJECTED";  // TODO: Notification.TYPE_COURSE_REJECTED가 생기면 교체
    private static final String NOTIFICATION_TITLE_REJECTED = "강의 검수 결과: 반려됐어요";

    private final CourseRepository courseRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final LessonRepository lessonRepository;
    private final QuizService quizService;                              // quiz 도메인은 Service로만
    private final NotificationService notificationService;
    private final Clock clock;                                          // Asia/Seoul 공통 시계

    /* API 89 상태별 강의 목록. 모르는 status 값은 필터 없이 전체 */
    public PageResponse<AdminCourseListItemDTO> findCourses(String status, Pageable pageable) {
        String statusFilter = status != null && STATUSES.contains(status) ? status : null;
        Page<Course> page = courseRepository.searchForAdmin(statusFilter, pageable);

        List<Long> courseIds = page.getContent().stream().map(Course::getCourseId).toList();
        Map<Long, Long> learners = countLearners(courseIds);
        Map<Long, String> instructorNames = findNicknames(
                page.getContent().stream().map(Course::getInstructorId).collect(Collectors.toSet()));

        return PageResponse.from(page.map(course -> AdminCourseListItemDTO.of(
                course,
                instructorNames.get(course.getInstructorId()),
                learners.getOrDefault(course.getCourseId(), 0L))));
    }

    /* API 90 검수 상세 */
    public AdminCourseDetailDTO getCourseDetail(Long courseId) {
        Course course = getCourseOrThrow(courseId);
        String instructorName = findNicknames(List.of(course.getInstructorId())).get(course.getInstructorId());
        return AdminCourseDetailDTO.of(course, instructorName,
                lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId),
                quizService.findQuizzesForReview(courseId),
                courseReviewRepository.findByCourseIdOrderByCreatedAtDescCourseReviewIdDesc(courseId));
    }

    /* API 91 허가 = 허가 + 공개. course_review 기록 */
    @Transactional
    public CourseReviewResultDTO approve(Long adminId, Long courseId) {
        Course course = getCourseOrThrow(courseId);
        course.approve(LocalDateTime.now(clock));
        courseReviewRepository.save(CourseReview.approved(courseId, adminId));
        // TODO: AdminLogService가 생기면 admin_log 기록 (DP-43, DBA② 요청함)
        return CourseReviewResultDTO.approved(course);
    }

    /* API 92 반려. 사유 필수, course_review 기록, 강사에게 알림 */
    @Transactional
    public CourseReviewResultDTO reject(Long adminId, Long courseId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(CourseErrorCode.REASON_REQUIRED);
        }
        Course course = getCourseOrThrow(courseId);
        course.reject();
        courseReviewRepository.save(CourseReview.rejected(courseId, adminId, reason.strip()));
        // TODO: AdminLogService가 생기면 admin_log 기록 (DP-43, DBA② 요청함)
        notificationService.send(course.getInstructorId(), NOTIFICATION_TYPE_REJECTED, NOTIFICATION_TITLE_REJECTED,
                "/instructor/courses/" + courseId);
        return CourseReviewResultDTO.rejected(course);
    }

    /* API 93 공개·비공개 (허가된 강의만, Course.changeVisibility가 확인) */
    @Transactional
    public void changeVisibility(Long courseId, boolean isPublic) {
        getCourseOrThrow(courseId).changeVisibility(isPublic);
    }

    /* API 94 삭제. 학습 기록이 있으면 삭제하지 않고 비공개만 하라고 안내 (SP-32) */
    @Transactional
    public void delete(Long courseId) {
        Course course = getCourseOrThrow(courseId);
        if (learningProgressRepository.existsByCourseId(courseId)) {
            throw new BusinessException(CourseErrorCode.COURSE_HAS_LEARNERS);
        }
        course.delete(LocalDateTime.now(clock));
    }

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .filter(course -> !course.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private Map<Long, Long> countLearners(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return learningProgressRepository.countLearnersByCourseIds(courseIds).stream()
                .collect(Collectors.toMap(
                        LearningProgressRepository.CourseLearnerCount::getCourseId,
                        LearningProgressRepository.CourseLearnerCount::getLearners));
    }

    /* 강사 이름(닉네임). TODO(#145): MemberService.findNicknames가 dev에 머지되면 호출로 교체 */
    private Map<Long, String> findNicknames(Collection<Long> memberIds) {
        return Map.of();
    }
}