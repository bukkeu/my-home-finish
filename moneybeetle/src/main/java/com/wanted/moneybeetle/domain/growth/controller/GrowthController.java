package com.wanted.moneybeetle.domain.growth.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   GET /api/v1/members/me/attendance, /members/me/cash-histories, /members/me/exp-histories, /levels
*   - 지금 구현된 것: 출석 현황(API 21). 캐시·경험치 내역과 레벨 목록은 각각의 이슈에서 이어서 붙인다.
*  */
@RestController
@RequiredArgsConstructor
public class GrowthController {

    private final AttendanceService attendanceService;

    // API 21. 출석 현황 (조회만 한다. 출석 기록은 로그인 성공 때 한다)
    @GetMapping("/api/v1/members/me/attendance")
    public ApiResponse<AttendanceStatusDTO> getAttendance(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(attendanceService.getStatus(me.getMemberId()));
    }
}
