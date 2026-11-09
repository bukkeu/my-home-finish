package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CourseStatusDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강의 생성·수정(허가 후 수정 시 재검수), 영상·요약글 저장, 검수 요청 조건 확인 (DP-34)
*   - 검수 요청 (API 81, #32)
*   - 내 강의 목록·생성·편집·수정·삭제 (API 72~76)는 #30에서 이 클래스에 추가
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorCourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final QuizService quizService;              // quiz 도메인은 Service로만
    private final Clock clock;                          // Asia/Seoul 공통 시계

    /* API 81 검수 요청: 본인 강의, 작성 중·반려 상태, 영상 1개 이상 + 테스트 퀴즈 5 + 퀴즈 5~15 (DP-34) */
    @Transactional
    public CourseStatusDTO requestReview(Long instructorId, Long courseId) {
        Course course = getMyCourse(instructorId, courseId);
        if (lessonRepository.countByCourseId(courseId) < 1) {
            throw new BusinessException(CourseErrorCode.NO_LESSON);
        }
        quizService.checkReadyForReview(courseId);
        course.requestReview(LocalDateTime.now(clock));
        return CourseStatusDTO.from(course);
    }

    /* 본인 강의만 (삭제된 강의는 404, 남의 강의는 403) */
    private Course getMyCourse(Long instructorId, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!course.isOwnedBy(instructorId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return course;
    }
}
