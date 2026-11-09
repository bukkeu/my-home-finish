package com.wanted.moneybeetle.domain.inquiry.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.inquiry.model.dto.AdminInquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryHiddenDTO;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
 *   담당 : DBA ① (문의)
 *   GET /api/v1/admin/inquiries, PATCH /api/v1/admin/inquiries/{inquiryId}/hidden (#38, ADM-07)
 *   권한: SecurityConfig가 /api/v1/admin/** 를 ADMIN만 허용 (로그인 안 함 401, 권한 없음 403)
 *  */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private static final int MAX_PAGE_SIZE = 50;

    private final InquiryService inquiryService;

    @GetMapping
    public ApiResponse<PageResponse<AdminInquiryListItemDTO>> list(@RequestParam(required = false) Long courseId,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) Boolean hidden,
                                                                   @RequestParam(required = false) Boolean deleted,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                inquiryService.findInquiriesForAdmin(courseId, status, hidden, deleted, toPageable(page, size)));
    }

    @PatchMapping("/{inquiryId}/hidden")
    public ResponseEntity<Void> changeHidden(@AuthenticationPrincipal AuthDetails admin,
                                             @PathVariable Long inquiryId,
                                             @Valid @RequestBody InquiryHiddenDTO request) {
        inquiryService.changeHidden(adminIdOf(admin), inquiryId, request.hidden());
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* 숨김 처리한 관리자(inquiry.hidden_by). AuthDetails가 없으면 안전한 쪽으로 401 */
    private Long adminIdOf(AuthDetails admin) {
        if (admin == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return admin.getMemberId();
    }
}