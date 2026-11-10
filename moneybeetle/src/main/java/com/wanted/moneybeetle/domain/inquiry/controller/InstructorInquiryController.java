package com.wanted.moneybeetle.domain.inquiry.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InstructorInquiryListDTO;
import com.wanted.moneybeetle.domain.inquiry.model.service.InstructorInquiryService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
 *   담당 : DBA ① (문의)
 *   GET /api/v1/instructor/inquiries (API 82, TCH-08, #32)
 *   권한: SecurityConfig가 /api/v1/instructor/** 를 승인된 강사(INSTRUCTOR)만 허용. 내 강의 확인은 서비스가 한다
 *  */
@RestController
@RequestMapping("/api/v1/instructor/inquiries")
@RequiredArgsConstructor
public class InstructorInquiryController {

    private static final int MAX_PAGE_SIZE = 50;

    private final InstructorInquiryService instructorInquiryService;

    @GetMapping
    public ApiResponse<InstructorInquiryListDTO> list(@AuthenticationPrincipal AuthDetails me,
                                                      @RequestParam(required = false) Long courseId,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(instructorInquiryService.findMyCourseInquiries(
                memberIdOf(me), courseId, status, toPageable(page, size)));
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* 로그인 강사 ID. 로그인 정보가 없으면 안전한 쪽으로 401 */
    private Long memberIdOf(AuthDetails me) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return me.getMemberId();
    }
}
