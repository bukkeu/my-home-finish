package com.wanted.moneybeetle.domain.home.controller;

/* comment.
*   담당 : DBA ② (#13 홈 API) · PM (#15 통합 검색 API)
*   GET /api/v1/home (오늘의 용어, 이어 볼 강의, 학습 기록, 트레이닝 해금), GET /api/v1/search
*   화면 : /user/home, /user/search
*  */

import com.wanted.moneybeetle.domain.home.model.dto.SearchResultDTO;
import com.wanted.moneybeetle.domain.home.model.service.HomeService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /* API 28 통합 검색 : GET /api/v1/search?keyword=청약 */
    @GetMapping("/api/v1/search")
    @ResponseBody
    public ApiResponse<SearchResultDTO> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(homeService.search(keyword, currentMemberId()));
    }

    // TODO(김예린 #6) 로그인 회원 번호 : AuthDetails.getMemberId() 가 생기면 바꾼다
    //  지금은 null → 종목은 검색 결과에 나오지 않는다 (해금 여부를 알 수 없음)
    private Long currentMemberId() {
        return null;
    }
}
