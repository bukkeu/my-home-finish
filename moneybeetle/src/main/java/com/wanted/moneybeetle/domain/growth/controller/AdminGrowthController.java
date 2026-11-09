package com.wanted.moneybeetle.domain.growth.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.dto.CashAdjustDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.CashAdjustResultDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleListDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardRuleUpdateDTO;
import com.wanted.moneybeetle.domain.growth.model.service.CashAdjustService;
import com.wanted.moneybeetle.domain.growth.model.service.RewardRuleService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   GET/PUT /api/v1/admin/reward-rules, POST /api/v1/admin/members/{memberId}/cash-adjustments
*   - 관리자만 쓸 수 있다 (SecurityConfig 가 /api/v1/admin/** 를 ADMIN 권한으로 막는다)
*  */
@RestController
@RequiredArgsConstructor
public class AdminGrowthController {

    private final RewardRuleService rewardRuleService;
    private final CashAdjustService cashAdjustService;

    // API 108. 보상 규칙 조회
    @GetMapping("/api/v1/admin/reward-rules")
    public ApiResponse<RewardRuleListDTO> getRewardRules() {
        return ApiResponse.success(rewardRuleService.findRules());
    }

    // API 109. 보상 규칙 수정
    @PutMapping("/api/v1/admin/reward-rules/{ruleId}")
    public ResponseEntity<Void> updateRewardRule(
            @AuthenticationPrincipal AuthDetails me,
            @PathVariable Long ruleId,
            @Valid @RequestBody RewardRuleUpdateDTO request) {
        rewardRuleService.updateRule(me.getMemberId(), ruleId, request);
        return ResponseEntity.noContent().build();
    }

    // API 110. 캐시 수동 지급·회수 (사유 필수, 회수로 0 미만이 되면 거절)
    @PostMapping("/api/v1/admin/members/{memberId}/cash-adjustments")
    public ResponseEntity<ApiResponse<CashAdjustResultDTO>> adjustCash(
            @AuthenticationPrincipal AuthDetails me,
            @PathVariable Long memberId,
            @Valid @RequestBody CashAdjustDTO request) {
        CashAdjustResultDTO result = cashAdjustService.adjust(me.getMemberId(), memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }
}
