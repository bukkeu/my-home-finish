package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.security.RequireLevel;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
/* comment.
*   담당 : SCM (성장·홈·모의 청약)
*   GET /api/v1/housing/complexes, /complexes/{complexId}, /account, /account/deposits, /applications/me, /applications/{id}/result
*   POST /api/v1/housing/account/deposits, /score-preview, /complexes/{complexId}/applications
*   화면 : /user/demo-housing-subscription
*  */
@RestController                                // ① JSON 을 돌려주는 컨트롤러 (#2 테스트 때 써 봄)
@RequestMapping("/api/v1/housing")
@RequiredArgsConstructor
public class HousingController {

    private final HousingService housingService;

    // API 63 청약 캘린더 : GET /api/v1/housing/complexes?month=2026-11
    @RequireLevel(UnlockFeature.HOUSING)     // Lv.12 (#4 에서 만든 것)
    @GetMapping("/complexes")                  // ② GET 요청 연결
    public ApiResponse<Map<String, List<ComplexDTO>>> findCalendar(@RequestParam YearMonth month) {
        List<ComplexDTO> items = housingService.findCalendar(month, currentMemberId());
        return ApiResponse.success(Map.of("items", items));   // ③ 성공 응답 (명세서 모양 { "items": [...] })
    }

    // API 64 공고 상세 : GET /api/v1/housing/complexes/{complexId}
    @RequireLevel(UnlockFeature.HOUSING)
    @GetMapping("/complexes/{complexId}")
    public ApiResponse<ComplexDetailDTO> getComplex(@PathVariable Long complexId) {   // ④ 주소 안의 {complexId} 꺼내기
        return ApiResponse.success(housingService.getComplex(complexId, currentMemberId()));
    }

    // TODO(김예린 #6) 로그인 회원 번호 : AuthDetails.getMemberId() 가 생기면 바꾼다
    //  지금은 null → 신청 여부는 모두 "안 함"으로 나온다
    private Long currentMemberId() {
        return null;
    }
}
