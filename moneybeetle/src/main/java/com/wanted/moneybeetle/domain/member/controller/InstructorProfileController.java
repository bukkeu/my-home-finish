package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorProfileUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.service.InstructorProfileService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (회원·인증)
*   GET   /api/v1/instructor/profile  내 강사 프로필 조회 (API 12-1)
*   PATCH /api/v1/instructor/profile  내 강사 프로필 수정 (API 12-2)
*   - /api/v1/instructor/** 는 SecurityConfig 가 INSTRUCTOR 권한만 들여보낸다. 학생 계정은 403
*   - 화면(강사 마이페이지)은 #360 (신아림)
*  */
@RestController
@RequiredArgsConstructor
public class InstructorProfileController {

    private final InstructorProfileService instructorProfileService;

    // API 12-1. 내 강사 프로필
    @GetMapping("/api/v1/instructor/profile")
    public ApiResponse<InstructorProfileDTO> getMyProfile(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(instructorProfileService.getMyProfile(me.getMemberId()));
    }

    // API 12-2. 내 강사 프로필 수정
    @PatchMapping("/api/v1/instructor/profile")
    public ApiResponse<InstructorProfileDTO> updateMyProfile(
            @AuthenticationPrincipal AuthDetails me,
            @Valid @RequestBody InstructorProfileUpdateDTO request) {
        return ApiResponse.success(instructorProfileService.updateMyProfile(me.getMemberId(), request));
    }
}
