package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.model.dto.*;
import com.wanted.moneybeetle.domain.housing.model.service.HousingApplicationService;
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

    private final HousingService housingService;                       // #27·#28 캘린더·공고·청약통장
    private final HousingApplicationService applicationService;        // #29 가점·신청·결과

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

    // API 68 가점 계산기 : POST /api/v1/housing/score-preview   (Lv.12)
    @RequireLevel(UnlockFeature.HOUSING)
    @PostMapping("/score-preview")
    public ApiResponse<ScoreDTO> previewScore(@RequestBody ScoreRequestDTO request,
                                              @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(applicationService.previewScore(auth.getMemberId(), request));
    }

    // API 69 청약 신청 : POST /api/v1/housing/complexes/{complexId}/applications   (Lv.12, 201)
    @RequireLevel(UnlockFeature.HOUSING)
    @PostMapping("/complexes/{complexId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplyResultDTO> apply(@PathVariable Long complexId,
                                             @RequestBody ScoreRequestDTO request,
                                             @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(applicationService.apply(auth.getMemberId(), complexId, request));
    }

    // API 70 내 신청 내역 : GET /api/v1/housing/applications/me   (Lv.12, 최신순)
    @RequireLevel(UnlockFeature.HOUSING)
    @GetMapping("/applications/me")
    public ApiResponse<PageResponse<ApplicationDTO>> findMyApplications(
            @PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(applicationService.findMyApplications(auth.getMemberId(), pageable));
    }

    // API 71 당첨 결과 : GET /api/v1/housing/applications/{applicationId}/result   (본인, 레벨 조건 없음)
    @GetMapping("/applications/{applicationId}/result")
    public ApiResponse<ApplicationResultDTO> getResult(@PathVariable Long applicationId,
                                                       @AuthenticationPrincipal AuthDetails auth) {
        return ApiResponse.success(applicationService.getResult(auth.getMemberId(), applicationId));
    }
}
