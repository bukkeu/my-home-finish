package com.wanted.moneybeetle.domain.course.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 89~94 관리자 강의 검수·공개 관리 (ADM-03, ADM-04)
 *   허가·반려는 course_review(검수 이력)와 admin_log(관리자 작업 기록, DP-43·DH-06)에 함께 남긴다
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCourseService {

    private static final Set<String> STATUSES = Set.of(
            Course.STATUS_DRAFT, Course.STATUS_REVIEW, Course.STATUS_APPROVED, Course.STATUS_REJECTED);
    private static final String NOTIFICATION_TYPE_REJECTED = "COURSE_REJECTED";  // TODO: Notification.TYPE_COURSE_REJECTED가 생기면 교체
    private static final String NOTIFICATION_TITLE_REJECTED = "강의 검수 결과: 반려됐어요";

    /* admin_log 값 (AdminLogService 주석의 예시와 같은 이름) */
    private static final String LOG_ACTION_APPROVE = "COURSE_APPROVE";
    private static final String LOG_ACTION_REJECT = "COURSE_REJECT";
    private static final String LOG_TARGET_COURSE = "COURSE";

    private final CourseRepository courseRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final LessonRepository lessonRepository;
    private final QuizService quizService;                              // quiz 도메인은 Service로만
    private final MemberService memberService;                          // 강사 이름(닉네임)
    private final NotificationService notificationService;
    private final AdminLogService adminLogService;                      // 관리자 작업 기록 (operation 도메인)
    private final ObjectMapper objectMapper;                            // admin_log detail JSON
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

    /* API 91 허가 = 허가 + 공개. course_review·admin_log 기록 */
    @Transactional
    public CourseReviewResultDTO approve(Long adminId, Long courseId) {
        Course course = getCourseForUpdate(courseId);
        Map<String, Object> before = stateOf(course);

        course.approve(LocalDateTime.now(clock));
        courseReviewRepository.save(CourseReview.approved(courseId, adminId));
        recordAdminLog(adminId, LOG_ACTION_APPROVE, courseId, before, stateOf(course), null);
        return CourseReviewResultDTO.approved(course);
    }

    /* API 92 반려. 사유 필수, course_review·admin_log 기록, 강사에게 알림 */
    @Transactional
    public CourseReviewResultDTO reject(Long adminId, Long courseId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(CourseErrorCode.REASON_REQUIRED);
        }
        String trimmedReason = reason.strip();
        Course course = getCourseForUpdate(courseId);
        Map<String, Object> before = stateOf(course);

        course.reject();
        courseReviewRepository.save(CourseReview.rejected(courseId, adminId, trimmedReason));
        recordAdminLog(adminId, LOG_ACTION_REJECT, courseId, before, stateOf(course), trimmedReason);
        notificationService.send(course.getInstructorId(), NOTIFICATION_TYPE_REJECTED, NOTIFICATION_TITLE_REJECTED,
                "/instructor/courses/" + courseId);
        return CourseReviewResultDTO.rejected(course);
    }

    /* API 93 공개·비공개 (허가된 강의만, Course.changeVisibility가 확인) */
    @Transactional
    public void changeVisibility(Long courseId, boolean isPublic) {
        getCourseForUpdate(courseId).changeVisibility(isPublic);
    }

    /* API 94 삭제. 학습 기록이 있으면 삭제하지 않고 비공개만 하라고 안내 (SP-32) */
    @Transactional
    public void delete(Long courseId) {
        Course course = getCourseForUpdate(courseId);
        if (learningProgressRepository.existsByCourseId(courseId)) {
            throw new BusinessException(CourseErrorCode.COURSE_HAS_LEARNERS);
        }
        course.delete(LocalDateTime.now(clock));
    }

    /* ===== 내부 도우미 ===== */

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .filter(course -> !course.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /* 허가·반려·공개 전환·삭제용: 행 잠금으로 읽는다. 강사의 수정·검수 요청과 순서대로 처리돼서
     * 검사한 상태와 저장하는 상태가 어긋나지 않는다 (#30, InstructorCourseService와 같은 규칙) */
    private Course getCourseForUpdate(Long courseId) {
        return courseRepository.findByIdForUpdate(courseId)
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

    /* 강사 이름(닉네임). member 도메인 Service로만 조회, 탈퇴 회원은 "탈퇴한 사용자" */
    private Map<Long, String> findNicknames(Collection<Long> memberIds) {
        return memberService.findNicknames(memberIds);
    }

    /* admin_log에 남길 강의 상태 (변경 전·후 비교용) */
    private Map<String, Object> stateOf(Course course) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", course.getStatus());
        state.put("isPublic", course.isPublic());
        return state;
    }

    /* 관리자 작업 기록. 같은 트랜잭션이라 허가·반려가 취소되면 기록도 함께 취소된다 (DH-06) */
    private void recordAdminLog(Long adminId, String action, Long courseId,
                                Map<String, Object> before, Map<String, Object> after, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        if (reason != null) {
            detail.put("reason", reason);
        }
        adminLogService.record(adminId, action, LOG_TARGET_COURSE, courseId, toJson(detail));
    }

    /* ObjectMapper로 만들어 사유에 따옴표 등이 있어도 올바른 JSON이 된다 */
    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("admin_log detail JSON 변환 실패", e);
        }
    }
}