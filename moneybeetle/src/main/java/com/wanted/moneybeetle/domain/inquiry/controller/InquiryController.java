package com.wanted.moneybeetle.domain.inquiry.controller;

import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryCreatedDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryDetailDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyCreateDTO;
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
*   POST /api/v1/inquiries/{inquiryId}/replies, GET /api/v1/instructor/inquiries
*   화면 : /user/inquiry
*  */
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private static final int MAX_PAGE_SIZE = 50;

    private final InquiryService inquiryService;

    @GetMapping
    public ApiResponse<PageResponse<InquiryListItemDTO>> list(@RequestParam(required = false) Long courseId,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                inquiryService.findInquiries(currentMemberId(), isAdmin(), courseId, status, toPageable(page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryCreatedDTO>> create(@Valid @RequestBody InquiryCreateDTO request) {
        Long inquiryId = inquiryService.createInquiry(currentMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(InquiryCreatedDTO.ofInquiry(inquiryId)));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryDetailDTO> detail(@PathVariable Long inquiryId) {
        return ApiResponse.success(inquiryService.getInquiry(currentMemberId(), isAdmin(), inquiryId));
    }

    @PatchMapping("/{inquiryId}")
    public ResponseEntity<Void> update(@PathVariable Long inquiryId,
                                       @Valid @RequestBody InquiryUpdateDTO request) {
        inquiryService.updateInquiry(currentMemberId(), inquiryId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<Void> delete(@PathVariable Long inquiryId) {
        inquiryService.deleteInquiry(currentMemberId(), isAdmin(), inquiryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{inquiryId}/replies")
    public ResponseEntity<ApiResponse<InquiryCreatedDTO>> reply(@PathVariable Long inquiryId,
                                                                @Valid @RequestBody InquiryReplyCreateDTO request) {
        Long replyId = inquiryService.createReply(currentMemberId(), isAdmin(), inquiryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(InquiryCreatedDTO.ofReply(replyId)));
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* TODO(#132): 로그인 연결 전에는 안전한 쪽으로 401 */
    private Long currentMemberId() {
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    /* TODO(#132): 로그인 연결 때 권한(ADMIN)으로 판단 */
    private boolean isAdmin() {
        return false;
    }
}