package com.wanted.moneybeetle.domain.trading.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.service.RankingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : PM (모의투자)
*   GET /api/v1/trading/seasons/{seasonId}/rankings?type=ASSET&page=0&size=10 (API 62, #26)
*   모의투자 Lv.8 해금 (LevelCheckInterceptor 가 확인). 관리자가 공개한 시즌만 (RANKING_NOT_PUBLISHED)
*  */
@RestController
@RequestMapping("/api/v1/trading")
@RequireLevel(UnlockFeature.TRADING)
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    // API 62 시즌 랭킹
    @GetMapping("/seasons/{seasonId}/rankings")
    public ApiResponse<RankingDTO> findRankings(@PathVariable Long seasonId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + RankingService.DEFAULT_SIZE) int size,
            @AuthenticationPrincipal AuthDetails me) {
        // 로그인 회원 번호는 주소로 받지 않고 세션에서 꺼낸다 (NFR-03)
        return ApiResponse.success(rankingService.findRankings(seasonId, type, page, size,
                me == null ? null : me.getMemberId()));
    }
}
