package com.wanted.moneybeetle.domain.growth.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.dto.AttendanceStatusDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashHistoryPageDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.ExpHistoryDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.ExpHistoryPageDTO;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   GET /api/v1/members/me/attendance, /members/me/cash-histories, /members/me/exp-histories, /levels
*   - 출석 현황(API 21), 캐시 내역(API 22), 경험치 내역(API 23)
*  */
@RestController
@RequiredArgsConstructor
public class GrowthController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AttendanceService attendanceService;
    private final RewardService rewardService;

    // API 21. 출석 현황 (조회만 한다. 출석 기록은 로그인 성공 때 한다)
    @GetMapping("/api/v1/members/me/attendance")
    public ApiResponse<AttendanceStatusDTO> getAttendance(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(attendanceService.getStatus(me.getMemberId()));
    }

    // API 22. 캐시 내역 (회원 번호는 주소가 아닌 로그인 정보에서 받는다)
    @GetMapping("/api/v1/members/me/cash-histories")
    public ApiResponse<CashHistoryPageDTO> getCashHistories(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String type) {
        Long memberId = me.getMemberId();
        MemberBalanceDTO balance = rewardService.getBalanceSnapshot(memberId);
        Pageable pageable = toPageable(page, size);
        return ApiResponse.success(CashHistoryPageDTO.from(
                balance.getCash(), rewardService.findCashHistories(memberId, type, pageable)));
    }

    // API 23. 경험치 내역
    @GetMapping("/api/v1/members/me/exp-histories")
    public ApiResponse<ExpHistoryPageDTO> getExpHistories(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Long memberId = me.getMemberId();
        MemberBalanceDTO balance = rewardService.getBalanceSnapshot(memberId);
        Pageable pageable = toPageable(page, size);
        return ApiResponse.success(ExpHistoryPageDTO.from(
                balance.getLevel(), balance.getExp(), rewardService.findExpHistories(memberId, pageable)));
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
