package com.wanted.moneybeetle.domain.trading.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingPublishedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingRewardResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatisticsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SettleResultDTO;
import com.wanted.moneybeetle.domain.trading.model.service.SettlementService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : PM (모의투자)
*   관리자 정산·랭킹 공개·보상·통계 (#46) : POST /seasons/{id}/settle, /rankings/publish, /rewards, GET /seasons/{id}/statistics
*   (참가자 목록·상세·랭킹 미리보기는 AdminParticipantController, #45)
*   권한 : /api/v1/admin/** 는 SecurityConfig 에서 ADMIN 만.
*   중복 요청 : SettlementService 가 시즌 행을 잠그고 상태·기존 수상 기록을 확인해 한 번만 처리한다
*   화면 : /admin/trading-management
*  */
@RestController
@RequestMapping("/api/v1/admin/trading/seasons/{seasonId}")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    // API 138 정산 실행 (되돌릴 수 없고 한 번만). 실행한 관리자는 로그인 세션에서 꺼낸다 (NFR-03)
    @PostMapping("/settle")
    public ApiResponse<SettleResultDTO> settle(@PathVariable Long seasonId,
            @AuthenticationPrincipal AuthDetails admin) {
        return ApiResponse.success(settlementService.settle(seasonId, adminIdOf(admin)));
    }

    // API 139 랭킹 공개 (정산 뒤에만)
    @PostMapping("/rankings/publish")
    public ApiResponse<RankingPublishedDTO> publishRanking(@PathVariable Long seasonId,
            @AuthenticationPrincipal AuthDetails admin) {
        return ApiResponse.success(settlementService.publishRanking(seasonId, adminIdOf(admin)));
    }

    // API 140 랭킹 보상 지급 (이미 지급한 수상은 건너뜀)
    @PostMapping("/rewards")
    public ApiResponse<RankingRewardResultDTO> grantRankingRewards(@PathVariable Long seasonId,
            @AuthenticationPrincipal AuthDetails admin) {
        return ApiResponse.success(settlementService.grantRankingRewards(seasonId, adminIdOf(admin)));
    }

    // API 141 시즌 통계
    @GetMapping("/statistics")
    public ApiResponse<SeasonStatisticsDTO> findStatistics(@PathVariable Long seasonId) {
        return ApiResponse.success(settlementService.findStatistics(seasonId));
    }

    private static Long adminIdOf(AuthDetails admin) {
        return admin == null ? null : admin.getMemberId();
    }
}
