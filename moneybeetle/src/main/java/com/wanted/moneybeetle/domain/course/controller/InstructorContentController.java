package com.wanted.moneybeetle.domain.course.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorQuizListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorSummaryListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.SummaryUpdateDTO;
import com.wanted.moneybeetle.domain.course.model.dto.SummaryUpdatedDTO;
import com.wanted.moneybeetle.domain.course.model.service.InstructorContentService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ① (강의·퀴즈) - 강사 센터 화면 작업을 DBA ② 김예린이 나눠 받아 만들었다 (#366)
*   GET /api/v1/instructor/quizzes (API 150), GET /api/v1/instructor/summaries (API 151),
*   PATCH /api/v1/instructor/lessons/{lessonId}/summary (API 152)
*   화면 : /instructor/dashboard?tab=quizzes, ?tab=summaries
*   - 권한: SecurityConfig가 /api/v1/instructor/** 를 승인된 강사(INSTRUCTOR)만 허용. 본인 강의 확인은 서비스가 한다
*  */
@RestController
@RequestMapping("/api/v1/instructor")
@RequiredArgsConstructor
public class InstructorContentController {

    private final InstructorContentService instructorContentService;

    /* API 150 퀴즈 목록 (내 강의마다 한 줄) */
    @GetMapping("/quizzes")
    public ApiResponse<InstructorQuizListDTO> quizzes(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(instructorContentService.findQuizzes(memberIdOf(me)));
    }

    /* API 151 요약글 목록 (요약글이 있는 내 영상마다 한 줄) */
    @GetMapping("/summaries")
    public ApiResponse<InstructorSummaryListDTO> summaries(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(instructorContentService.findSummaries(memberIdOf(me)));
    }

    /* API 152 영상 요약글 하나만 수정 */
    @PatchMapping("/lessons/{lessonId}/summary")
    public ApiResponse<SummaryUpdatedDTO> updateSummary(@AuthenticationPrincipal AuthDetails me,
                                                        @PathVariable Long lessonId,
                                                        @Valid @RequestBody SummaryUpdateDTO request) {
        return ApiResponse.success(instructorContentService.updateSummary(memberIdOf(me), lessonId, request));
    }

    /* 로그인 강사 ID. 로그인 정보가 없으면 안전한 쪽으로 401 */
    private Long memberIdOf(AuthDetails me) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return me.getMemberId();
    }
}
