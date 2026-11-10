package com.wanted.moneybeetle.domain.trading.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnCheckDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.NextTurnRequestDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnAdvancedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TurnCalendarDTO;
import com.wanted.moneybeetle.domain.trading.model.service.TurnAdminService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : PM (모의투자)
*   관리자 턴 관리 (#44) : GET /seasons/{id}/turns, GET /turns/next-check, POST /turns/next
*   권한 : /api/v1/admin/** 는 SecurityConfig 에서 ADMIN 만
*   화면 : /admin/trading-management
*  */
@RestController
@RequestMapping("/api/v1/admin/trading/seasons/{seasonId}/turns")
@RequiredArgsConstructor
public class AdminTurnController {

    private final TurnAdminService turnAdminService;

    // API 118 턴 달력·진행 기록
    @GetMapping
    public ApiResponse<TurnCalendarDTO> findTurns(@PathVariable Long seasonId) {
        return ApiResponse.success(turnAdminService.findTurns(seasonId));
    }

    // API 119 다음 턴 점검
    @GetMapping("/next-check")
    public ApiResponse<NextTurnCheckDTO> checkNextTurn(@PathVariable Long seasonId) {
        return ApiResponse.success(turnAdminService.checkNextTurn(seasonId));
    }

    // API 120 다음 턴 진행 (되돌릴 수 없음). 넘긴 관리자는 로그인 세션에서 꺼낸다 (NFR-03)
    @PostMapping("/next")
    public ApiResponse<TurnAdvancedDTO> advanceTurn(@PathVariable Long seasonId,
            @Valid @RequestBody NextTurnRequestDTO request, @AuthenticationPrincipal AuthDetails admin) {
        return ApiResponse.success(turnAdminService.advanceTurn(seasonId, request.expectedCurrentTurn(),
                admin == null ? null : admin.getMemberId()));
    }
}
