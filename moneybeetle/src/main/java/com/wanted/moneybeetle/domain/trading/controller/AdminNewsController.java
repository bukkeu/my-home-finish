package com.wanted.moneybeetle.domain.trading.controller;

/* comment.
*   담당 : PM (모의투자)
*   관리자 가상 뉴스 (#43) : GET/POST /api/v1/admin/trading/seasons/{seasonId}/news, PUT/DELETE /news/{newsId}
*   권한 : /api/v1/admin/** 는 SecurityConfig 에서 ADMIN 만
*   분야·종목·가격표를 다루는 AdminMarketController 와 나눠 둔다 (#41·#42 PR 과 파일이 겹치지 않게)
*  */

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminNewsDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.NewsSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.service.NewsAdminService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/trading")
@RequiredArgsConstructor
public class AdminNewsController {

    private final NewsAdminService newsAdminService;

    // API 131 뉴스 목록 : GET /api/v1/admin/trading/seasons/{seasonId}/news?turnNo=6
    @GetMapping("/seasons/{seasonId}/news")
    public ApiResponse<Map<String, List<AdminNewsDTO>>> findNews(@PathVariable Long seasonId,
            @RequestParam(required = false) Integer turnNo) {
        return ApiResponse.success(Map.of("items", newsAdminService.findNews(seasonId, turnNo)));
    }

    // API 132 뉴스 등록 (201)
    @PostMapping("/seasons/{seasonId}/news")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> createNews(@PathVariable Long seasonId,
            @Valid @RequestBody NewsSaveDTO request) {
        return ApiResponse.success(Map.of("newsId", newsAdminService.createNews(seasonId, request)));
    }

    // API 133 뉴스 수정 (204). 지난 턴 뉴스 수정은 로그인 관리자 번호로 작업 기록을 남긴다 (#340)
    @PutMapping("/news/{newsId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeNews(@PathVariable Long newsId, @Valid @RequestBody NewsSaveDTO request,
            @AuthenticationPrincipal AuthDetails admin) {
        newsAdminService.changeNews(newsId, request, admin == null ? null : admin.getMemberId());
    }

    // API 134 뉴스 삭제 (204)
    @DeleteMapping("/news/{newsId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNews(@PathVariable Long newsId) {
        newsAdminService.deleteNews(newsId);
    }
}
