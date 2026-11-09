package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.model.dto.AdminComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexRequestDTO;
import com.wanted.moneybeetle.domain.housing.model.service.AdminHousingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/* comment.
*   담당 : SCM (모의 청약)
*   #47 관리자 공고 관리 (API 142~145) - /api/v1/admin/** 은 SecurityConfig 에서 ADMIN 만 통과
*   신청자 목록·산정·발표(GET /complexes/{id}/applications, POST /compute, /announce)는 #48
*   화면 : /admin/housing-management (#70)
*  */
@RestController
@RequestMapping("/api/v1/admin/housing")
@RequiredArgsConstructor
public class AdminHousingController {

    private final AdminHousingService adminHousingService;

    // API 142 단지 목록 → { "items": [...] }
    @GetMapping("/complexes")
    public ApiResponse<Map<String, List<AdminComplexDTO>>> findComplexes() {
        return ApiResponse.success(Map.of("items", adminHousingService.findComplexes()));
    }

    // API 143 공고 등록 → 201 { "complexId": 1 }, 등록한 관리자 번호는 로그인 세션에서
    @PostMapping("/complexes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> createComplex(@RequestBody ComplexRequestDTO request,
                                                        @AuthenticationPrincipal AuthDetails auth) {
        Long complexId = adminHousingService.createComplex(auth.getMemberId(), request);
        return ApiResponse.success(Map.of("complexId", complexId));
    }

    // API 144 공고 수정 → 204 (본문 없음)
    @PutMapping("/complexes/{complexId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateComplex(@PathVariable Long complexId,
                              @RequestBody ComplexRequestDTO request) {
        adminHousingService.updateComplex(complexId, request);
    }

    // API 145 공고 삭제(Soft Delete) → 204
    @DeleteMapping("/complexes/{complexId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComplex(@PathVariable Long complexId) {
        adminHousingService.deleteComplex(complexId);
    }
}
