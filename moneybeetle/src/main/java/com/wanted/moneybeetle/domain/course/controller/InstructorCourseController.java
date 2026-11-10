package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.course.model.dto.CourseStatusDTO;
import com.wanted.moneybeetle.domain.course.model.service.InstructorCourseService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   GET/POST /api/v1/instructor/courses, GET/PUT/DELETE /api/v1/instructor/courses/{courseId}
*   POST /thumbnail, PUT /lessons, PUT /test-quiz, PUT /quiz, POST /review-request
*   화면 : /instructor/dashboard, /instructor/course-create
*   - 현재: POST /review-request (API 81, #32). 나머지는 #30·#31에서 추가
*   - 권한: SecurityConfig가 /api/v1/instructor/** 를 승인된 강사(INSTRUCTOR)만 허용. 본인 강의 확인은 서비스가 한다
*  */
@RestController
@RequestMapping("/api/v1/instructor/courses")
@RequiredArgsConstructor
public class InstructorCourseController {

    private final InstructorCourseService instructorCourseService;

    /* API 81 검수 요청 */
    @PostMapping("/{courseId}/review-request")
    public ApiResponse<CourseStatusDTO> requestReview(@AuthenticationPrincipal AuthDetails me,
                                                      @PathVariable Long courseId) {
        return ApiResponse.success(instructorCourseService.requestReview(memberIdOf(me), courseId));
    }

    /* 로그인 강사 ID. 로그인 정보가 없으면 안전한 쪽으로 401 */
    private Long memberIdOf(AuthDetails me) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return me.getMemberId();
    }
}
