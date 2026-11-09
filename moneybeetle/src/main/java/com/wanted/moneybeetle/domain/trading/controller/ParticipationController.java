package com.wanted.moneybeetle.domain.trading.controller;

/* comment.
*   담당 : PM (모의투자)
*   모의투자 홈·시즌 참가 (#23) : GET /api/v1/trading/home, POST /api/v1/trading/seasons/{seasonId}/participations
*   Lv.8 해금 (LevelCheckInterceptor). 시세·뉴스 조회(TradingController, #24 PR)와 파일이 겹치지 않게 나눠 둔다
*  */

import com.wanted.moneybeetle.domain.trading.model.dto.ParticipationResultDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingHomeDTO;
import com.wanted.moneybeetle.domain.trading.model.service.ParticipationService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public ApiResponse<TradingHomeDTO> getHome() {
        return ApiResponse.success(participationService.getHome(currentMemberId()));
    }

    // API 54 시즌 참가 (201)
    @PostMapping("/seasons/{seasonId}/participations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParticipationResultDTO> join(@PathVariable Long seasonId) {
        return ApiResponse.success(participationService.join(seasonId, currentMemberId()));
    }

    // TODO(김예린 #6) 로그인 회원 번호 : AuthDetails.getMemberId() 가 생기면 바꾼다
    //  지금은 null → 홈은 참가 정보 없이 보이고, 참가는 Service 가 401 UNAUTHORIZED 로 막는다
    private Long currentMemberId() {
        return null;
    }
}
