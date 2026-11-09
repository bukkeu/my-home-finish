package com.wanted.moneybeetle.domain.term.controller;

import com.wanted.moneybeetle.domain.term.model.dto.TodayTermsDTO;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (경제 용어)
*   GET /api/v1/economic-terms/today
*   - 로그인한 회원이면 누구나 볼 수 있다 (SecurityConfig 의 anyRequest().authenticated())
*  */
@RestController
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    // API 20. 오늘의 경제 용어
    @GetMapping("/api/v1/economic-terms/today")
    public ApiResponse<TodayTermsDTO> getToday() {
        return ApiResponse.success(termService.getToday());
    }
}
