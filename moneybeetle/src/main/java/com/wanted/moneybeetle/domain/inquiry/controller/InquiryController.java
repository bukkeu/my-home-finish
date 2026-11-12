package com.wanted.moneybeetle.domain.inquiry.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryCreatedDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryDetailDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyUpdateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryUpdateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.service.InquiryService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
 *   담당 : DBA ① (문의)
 *   GET/POST /api/v1/inquiries, GET/PATCH/DELETE /api/v1/inquiries/{inquiryId}
 *   POST /api/v1/inquiries/{inquiryId}/replies   (API 47~52, #22)
 *   PATCH/DELETE /api/v1/inquiries/{inquiryId}/replies/{replyId} (API 52-1·52-2 답변 수정·삭제, #370)
 *   GET /api/v1/instructor/inquiries             (API 82, #32에서 추가)
 *   화면 : /user/inquiry
 *   로그인 회원: @AuthenticationPrincipal AuthDetails (#132). 관리자 여부는 권한(role)이 ADMIN인지로 판단
 *  */
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String ROLE_ADMIN = "ADMIN";   // AuthDetails 권한 이름 (ROLE_ 접두어 없음, SecurityConfig와 같음)

    private final InquiryService inquiryService;

    @GetMapping
    public ApiResponse<PageResponse<InquiryListItemDTO>> list(@AuthenticationPrincipal AuthDetails me,
                                                              @RequestParam(required = false) Long courseId,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                inquiryService.findInquiries(memberIdOf(me), isAdmin(me), courseId, status, toPageable(page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryCreatedDTO>> create(@AuthenticationPrincipal AuthDetails me,
                                                                 @Valid @RequestBody InquiryCreateDTO request) {
        Long inquiryId = inquiryService.createInquiry(memberIdOf(me), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(InquiryCreatedDTO.ofInquiry(inquiryId)));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryDetailDTO> detail(@AuthenticationPrincipal AuthDetails me,
                                                @PathVariable Long inquiryId) {
        return ApiResponse.success(inquiryService.getInquiry(memberIdOf(me), isAdmin(me), inquiryId));
    }

    @PatchMapping("/{inquiryId}")
    public ResponseEntity<Void> update(@AuthenticationPrincipal AuthDetails me,
                                       @PathVariable Long inquiryId,
                                       @Valid @RequestBody InquiryUpdateDTO request) {
        inquiryService.updateInquiry(memberIdOf(me), inquiryId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthDetails me,
                                       @PathVariable Long inquiryId) {
        inquiryService.deleteInquiry(memberIdOf(me), isAdmin(me), inquiryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{inquiryId}/replies")
    public ResponseEntity<ApiResponse<InquiryCreatedDTO>> reply(@AuthenticationPrincipal AuthDetails me,
                                                                @PathVariable Long inquiryId,
                                                                @Valid @RequestBody InquiryReplyCreateDTO request) {
        Long replyId = inquiryService.createReply(memberIdOf(me), isAdmin(me), inquiryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(InquiryCreatedDTO.ofReply(replyId)));
    }

    @PatchMapping("/{inquiryId}/replies/{replyId}")
    public ResponseEntity<Void> updateReply(@AuthenticationPrincipal AuthDetails me,
                                            @PathVariable Long inquiryId,
                                            @PathVariable Long replyId,
                                            @Valid @RequestBody InquiryReplyUpdateDTO request) {
        inquiryService.updateReply(memberIdOf(me), inquiryId, replyId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{inquiryId}/replies/{replyId}")
    public ResponseEntity<Void> deleteReply(@AuthenticationPrincipal AuthDetails me,
                                            @PathVariable Long inquiryId,
                                            @PathVariable Long replyId) {
        inquiryService.deleteReply(memberIdOf(me), inquiryId, replyId);
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* 로그인 회원 ID. 로그인 정보가 없으면 안전한 쪽으로 401 */
    private Long memberIdOf(AuthDetails me) {
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return me.getMemberId();
    }

    private boolean isAdmin(AuthDetails me) {
        return me != null && ROLE_ADMIN.equals(me.getRole());
    }
}