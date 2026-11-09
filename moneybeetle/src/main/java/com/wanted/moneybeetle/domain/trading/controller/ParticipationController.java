package com.wanted.moneybeetle.domain.trading.controller;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 홈·시즌 참가 (#23) : GET /api/v1/trading/home, POST /api/v1/trading/seasons/{seasonId}/participations
*   Lv.8 해금 (LevelCheckInterceptor). 시세·뉴스 조회(TradingController, #24 PR)와 파일이 겹치지 않게 나눠 둔다
*  */

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.ParticipationResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingHomeDTO;
import com.wanted.moneybeetle.domain.trading.model.service.ParticipationService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trading")
@RequireLevel(UnlockFeature.TRADING)
@RequiredArgsConstructor
public class ParticipationController {

    private final ParticipationService participationService;

    // API 53 모의투자 홈
    @GetMapping("/home")
    public ApiResponse<TradingHomeDTO> getHome(@AuthenticationPrincipal AuthDetails me) {
        // 로그인 회원 번호는 주소로 받지 않고 세션에서 꺼낸다 (NFR-03). 없으면 참가 정보 없이 보인다
        return ApiResponse.success(participationService.getHome(memberIdOf(me)));
    }

    // API 54 시즌 참가 (201)
    @PostMapping("/seasons/{seasonId}/participations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParticipationResultDTO> join(@PathVariable Long seasonId, @AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(participationService.join(seasonId, memberIdOf(me)));
    }

    // 로그인 정보가 없으면 null: 홈은 참가 정보 없이 보이고, 참가는 Service 가 401 UNAUTHORIZED 로 막는다
    private static Long memberIdOf(AuthDetails me) {
        return me == null ? null : me.getMemberId();
    }
}
