package com.wanted.moneybeetle.domain.operation.controller;

import com.wanted.moneybeetle.domain.operation.model.dto.DashboardDTO;
import com.wanted.moneybeetle.domain.operation.model.service.DashboardService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   GET /api/v1/admin/dashboard
*   - 관리자만 쓸 수 있다 (SecurityConfig 가 /api/v1/admin/** 를 ADMIN 권한으로 막는다)
*  */
@RestController
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    // API 84. 대시보드
    @GetMapping("/api/v1/admin/dashboard")
    public ApiResponse<DashboardDTO> getDashboard() {
        return ApiResponse.success(dashboardService.getDashboard());
    }
}
