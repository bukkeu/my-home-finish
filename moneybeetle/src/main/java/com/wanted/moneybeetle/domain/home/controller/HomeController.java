package com.wanted.moneybeetle.domain.home.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.dto.HomeDTO;
import com.wanted.moneybeetle.domain.home.model.service.HomeService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (#13 홈 API) · PM (#15 통합 검색 API)
*   GET /api/v1/home (오늘의 용어, 이어 볼 강의, 학습 기록, 트레이닝 해금), GET /api/v1/search
*   화면 : /user/home, /user/search
*   - 지금 구현된 것: 홈 화면(API 19). 통합 검색(API 28)은 #15 에서 이어서 붙인다.
*  */
@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    // API 19. 홈 화면
    @GetMapping("/api/v1/home")
    public ApiResponse<HomeDTO> getHome(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(homeService.getHome(me.getMemberId()));
    }
}
