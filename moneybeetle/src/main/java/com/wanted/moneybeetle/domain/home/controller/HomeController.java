package com.wanted.moneybeetle.domain.home.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.dto.HomeDTO;
import com.wanted.moneybeetle.domain.home.model.dto.SearchResultDTO;
import com.wanted.moneybeetle.domain.home.model.service.HomeService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (#13 홈 API) · PM (#15 통합 검색 API)
*   GET /api/v1/home (오늘의 용어, 이어 볼 강의, 학습 기록, 트레이닝 해금), GET /api/v1/search
*   화면 : /user/home, /user/search
*   - 홈 화면(API 19, #13), 통합 검색(API 28, #15)
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

    /* API 28 통합 검색 : GET /api/v1/search?keyword=청약 */
    @GetMapping("/api/v1/search")
    public ApiResponse<SearchResultDTO> search(@RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthDetails me) {
        // 로그인 회원 번호는 주소로 받지 않고 세션에서 꺼낸다 (NFR-03). 없으면 종목은 검색 결과에 나오지 않는다
        return ApiResponse.success(homeService.search(keyword, me == null ? null : me.getMemberId()));
    }
}
