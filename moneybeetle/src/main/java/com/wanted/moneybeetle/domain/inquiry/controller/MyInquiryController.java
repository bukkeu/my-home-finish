package com.wanted.moneybeetle.domain.inquiry.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.inquiry.model.dto.MyInquiryItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
 *   담당 : DBA ① 신아림 (#71, 마이페이지 Q&A·문의 모음)
 *   API 149 GET /api/v1/members/me/inquiries : 내가 쓴 문의 전체(사이트 문의 + 강의 문의), 최신순
 *   문의 목록(API 47)은 courseId 하나로만 거르므로(없으면 사이트 문의만) 마이페이지에서 한 번에 모아 볼 수 없어 따로 둔다
 *  */
@RestController
@RequiredArgsConstructor
public class MyInquiryController {

    private static final int MAX_PAGE_SIZE = 50;   // API 47 과 같은 상한

    private final InquiryService inquiryService;

    @GetMapping("/api/v1/members/me/inquiries")
    public ApiResponse<PageResponse<MyInquiryItemDTO>> myInquiries(@AuthenticationPrincipal AuthDetails me,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(inquiryService.findMyInquiries(me.getMemberId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE))));
    }
}
