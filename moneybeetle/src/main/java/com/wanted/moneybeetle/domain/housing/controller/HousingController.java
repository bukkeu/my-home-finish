package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.model.dto.*;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/* comment.
*   담당 : SCM (모의 청약)
*   GET /api/v1/housing/complexes, /complexes/{complexId}, /account, /account/deposits, /applications/me, /applications/{id}/result
*   POST /api/v1/housing/account/deposits, /score-preview, /complexes/{complexId}/applications
*   화면 : /user/demo-housing-subscription
*   회원 번호는 주소·요청 값이 아니라 로그인 세션(@AuthenticationPrincipal AuthDetails)에서 꺼낸다 (본인 데이터 규칙)
*  */
@RestController
@RequestMapping("/api/v1/housing")
@RequiredArgsConstructor
public class HousingController {

    private final HousingService housingService;

    // API 63 청약 캘린더 : GET /api/v1/housing/complexes?month=2026-11   (Lv.12)
    @RequireLevel(UnlockFeature.HOUSING)
    @GetMapping("/complexes")
    public ApiResponse<Map<String, List<ComplexDTO>>> findCalendar(@RequestParam YearMonth month,
                                                                   @AuthenticationPrincipal AuthDetails auth) {
        List<ComplexDTO> items = housingService.findCalendar(month, auth.getMemberId());
        return ApiResponse.success(Map.of("items", items));   // 명세서 모양 { "items": [...] }
    }

    // API 64 공고 상세 : GET /api/v1/housing/complexes/{complexId}   (Lv.12)
    @RequireLevel(UnlockFeature.HOUSING)
    @GetMapping("/complexes/{complexId}")
    public ApiResponse<ComplexDetailDTO> getComplex(@PathVariable Long complexId,
                                                    @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(housingService.getComplex(complexId, auth.getMemberId()));
    }

    // API 65 내 청약통장 : GET /api/v1/housing/account   (레벨 조건 없음)
    @GetMapping("/account")
    public ApiResponse<AccountDTO> getAccount(@AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(housingService.getAccount(auth.getMemberId()));
    }

    // API 66 청약통장 납입 : POST /api/v1/housing/account/deposits   { "amount": 300000 }   (레벨 조건 없음)
    @PostMapping("/account/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DepositResultDTO> deposit(@RequestBody DepositRequestDTO request,
                                                 @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(housingService.deposit(auth.getMemberId(), request.getAmount()));
    }

    // API 67 납입 내역 : GET /api/v1/housing/account/deposits?page=0&size=20   (최신순, 레벨 조건 없음)
    @GetMapping("/account/deposits")
    public ApiResponse<PageResponse<DepositDTO>> findDeposits(
            @PageableDefault(size = 20, sort = "paidDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(housingService.findDeposits(auth.getMemberId(), pageable));
    }
}
