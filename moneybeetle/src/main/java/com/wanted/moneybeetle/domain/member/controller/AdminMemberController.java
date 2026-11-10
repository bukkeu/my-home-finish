package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.AdminMemberItemDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorApplicationDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberRoleChangeDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberStatusChangeDTO;
import com.wanted.moneybeetle.domain.member.model.service.AdminMemberService;
import com.wanted.moneybeetle.domain.member.model.service.InstructorService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   GET   /api/v1/admin/members (API 101), /api/v1/admin/instructor-applications (API 104)
*   PATCH /api/v1/admin/members/{memberId}/status (API 102), /role (API 103)
*   POST  /api/v1/admin/instructor-applications/{memberId}/approve (API 105)
*   - 권한: SecurityConfig 가 /api/v1/admin/** 를 ADMIN 만 허용
*  */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMemberController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminMemberService adminMemberService;
    private final InstructorService instructorService;

    /* API 101 사용자·강사 목록 (role·status·keyword 모두 선택) */
    @GetMapping("/members")
    public ApiResponse<PageResponse<AdminMemberItemDTO>> members(@RequestParam(required = false) String role,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminMemberService.findMembers(role, status, keyword, toPageable(page, size)));
    }

    /* API 102 계정 정지·해제 → 204 */
    @PatchMapping("/members/{memberId}/status")
    public ResponseEntity<Void> changeStatus(@AuthenticationPrincipal AuthDetails admin,
                                             @PathVariable Long memberId,
                                             @Valid @RequestBody MemberStatusChangeDTO request) {
        adminMemberService.changeStatus(adminIdOf(admin), memberId, request.status(), request.reason());
        return ResponseEntity.noContent().build();
    }

    /* API 103 강사 권한 부여·회수 → 204 */
    @PatchMapping("/members/{memberId}/role")
    public ResponseEntity<Void> changeRole(@AuthenticationPrincipal AuthDetails admin,
                                           @PathVariable Long memberId,
                                           @Valid @RequestBody MemberRoleChangeDTO request) {
        adminMemberService.changeRole(adminIdOf(admin), memberId, request.role());
        return ResponseEntity.noContent().build();
    }

    /* API 104 강사 가입 신청 목록 (status 기본 PENDING) */
    @GetMapping("/instructor-applications")
    public ApiResponse<PageResponse<InstructorApplicationDTO>> applications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(instructorService.findApplications(status, toPageable(page, size)));
    }

    /* API 105 강사 승인 → 204 */
    @PostMapping("/instructor-applications/{memberId}/approve")
    public ResponseEntity<Void> approve(@AuthenticationPrincipal AuthDetails admin, @PathVariable Long memberId) {
        instructorService.approve(adminIdOf(admin), memberId);
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /* admin_log·승인 기록에 남길 관리자 ID. 로그인 정보가 없으면 안전한 쪽으로 401 */
    private Long adminIdOf(AuthDetails admin) {
        if (admin == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return admin.getMemberId();
    }
}
