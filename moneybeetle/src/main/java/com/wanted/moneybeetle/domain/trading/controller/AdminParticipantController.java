package com.wanted.moneybeetle.domain.trading.controller;

import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantDetailDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.AdminParticipantListDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.RankingDTO;
import com.wanted.moneybeetle.domain.trading.model.service.AdminParticipantService;
import com.wanted.moneybeetle.domain.trading.model.service.RankingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : PM (모의투자)
*   관리자 참가자 관리·랭킹 미리보기 (#45) : GET /seasons/{id}/participants, /participants/{id}, /seasons/{id}/rankings/preview
*   권한 : /api/v1/admin/** 는 SecurityConfig 에서 ADMIN 만 (미리보기는 관리자만 호출할 수 있다)
*   화면 : /admin/trading-management
*  */
@RestController
@RequestMapping("/api/v1/admin/trading")
@RequiredArgsConstructor
public class AdminParticipantController {

    private final AdminParticipantService participantService;

    // API 135 참가자 목록
    @GetMapping("/seasons/{seasonId}/participants")
    public ApiResponse<AdminParticipantListDTO> findParticipants(@PathVariable Long seasonId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AdminParticipantService.DEFAULT_SIZE) int size) {
        return ApiResponse.success(participantService.findParticipants(seasonId, sort, page, size));
    }

    // API 136 참가자 상세
    @GetMapping("/participants/{participantId}")
    public ApiResponse<AdminParticipantDetailDTO> getParticipant(@PathVariable Long participantId) {
        return ApiResponse.success(participantService.getParticipant(participantId));
    }

    // API 137 랭킹 미리보기
    @GetMapping("/seasons/{seasonId}/rankings/preview")
    public ApiResponse<RankingDTO> previewRankings(@PathVariable Long seasonId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + RankingService.DEFAULT_SIZE) int size) {
        return ApiResponse.success(participantService.previewRankings(seasonId, type, page, size));
    }
}
