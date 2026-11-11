package com.wanted.moneybeetle.domain.trading.controller;

/* comment.
*   담당 : PM (모의투자)
*   관리자 시즌 관리 (#40) : GET/POST /api/v1/admin/trading/seasons, PATCH /seasons/{id}/settings, /status
*   턴 달력·다음 턴(GET /seasons/{id}/turns, /turns/next-check, POST /turns/next)은 AdminTurnController (#44)
*   권한 : /api/v1/admin/** 는 SecurityConfig 에서 ADMIN 만
*   화면 : /admin/trading-management
*  */

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonCreatedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSettingDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatusDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SeasonAdminService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/trading/seasons")
@RequiredArgsConstructor
public class AdminSeasonController {

    private final SeasonAdminService seasonAdminService;

    // API 114 시즌 목록
    @GetMapping
    public ApiResponse<Map<String, List<SeasonSummaryDTO>>> findSeasons() {
        return ApiResponse.success(Map.of("items", seasonAdminService.findSeasons()));
    }

    // API 115 시즌 생성 (201)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SeasonCreatedDTO> createSeason(@Valid @RequestBody SeasonSaveDTO request,
            @AuthenticationPrincipal AuthDetails admin) {
        // 시즌을 만든 관리자(trading_season.created_by)는 주소·본문이 아니라 로그인 세션에서 꺼낸다 (NFR-03)
        return ApiResponse.success(seasonAdminService.createSeason(request, admin == null ? null : admin.getMemberId()));
    }

    // API 116 시즌 설정 (204)
    @PatchMapping("/{seasonId}/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeSetting(@PathVariable Long seasonId, @Valid @RequestBody SeasonSettingDTO request) {
        seasonAdminService.changeSetting(seasonId, request);
    }

    // API 117 시즌 상태 변경 (204)
    @PatchMapping("/{seasonId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@PathVariable Long seasonId, @Valid @RequestBody SeasonStatusDTO request) {
        seasonAdminService.changeStatus(seasonId, request);
    }
}
