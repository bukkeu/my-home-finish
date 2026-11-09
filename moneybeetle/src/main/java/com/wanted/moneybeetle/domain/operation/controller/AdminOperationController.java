package com.wanted.moneybeetle.domain.operation.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.operation.model.dto.AdminLogListDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.SettingListDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.SettingUpdateDTO;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogQueryService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   GET/PUT /api/v1/admin/settings, GET /api/v1/admin/admin-logs
*   - 관리자만 쓸 수 있다 (SecurityConfig 가 /api/v1/admin/** 를 ADMIN 권한으로 막는다)
*  */
@RestController
@RequiredArgsConstructor
public class AdminOperationController {

    private final SystemSettingService systemSettingService;
    private final AdminLogQueryService adminLogQueryService;

    // API 111. 서비스 설정
    @GetMapping("/api/v1/admin/settings")
    public ApiResponse<SettingListDTO> getSettings() {
        return ApiResponse.success(systemSettingService.findSettings());
    }

    // API 112. 서비스 설정 변경
    @PutMapping("/api/v1/admin/settings/{key}")
    public ResponseEntity<Void> updateSetting(
            @AuthenticationPrincipal AuthDetails me,
            @PathVariable String key,
            @Valid @RequestBody SettingUpdateDTO request) {
        systemSettingService.updateSetting(me.getMemberId(), key, request.value());
        return ResponseEntity.noContent().build();
    }

    // API 113. 관리자 작업 기록 (대상·관리자·기간으로 조회, 모두 선택)
    @GetMapping("/api/v1/admin/admin-logs")
    public ApiResponse<AdminLogListDTO> getAdminLogs(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(adminLogQueryService.findLogs(targetType, targetId, adminId, from, to, page, size));
    }
}
