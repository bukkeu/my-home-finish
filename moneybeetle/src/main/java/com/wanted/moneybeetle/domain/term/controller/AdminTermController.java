package com.wanted.moneybeetle.domain.term.controller;

import com.wanted.moneybeetle.domain.term.model.dto.AdminTermListDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreateDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermCreatedDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermScheduleListDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermScheduleUpdateDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TermUpdateDTO;
import com.wanted.moneybeetle.domain.term.model.service.EconomicTermService;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* comment.
*   담당 : DBA ② (경제 용어)
*   GET/POST/PUT/DELETE /api/v1/admin/economic-terms, GET /api/v1/admin/term-schedules, PUT /admin/term-schedules/{date}
*   - 관리자만 쓸 수 있다 (SecurityConfig 가 /api/v1/admin/** 를 ADMIN 권한으로 막는다)
*  */
@RestController
@RequiredArgsConstructor
public class AdminTermController {

    private final EconomicTermService economicTermService;
    private final TermService termService;

    // API 95. 경제 용어 목록 (등록 순)
    @GetMapping("/api/v1/admin/economic-terms")
    public ApiResponse<AdminTermListDTO> getTerms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(economicTermService.findTerms(keyword, page, size));
    }

    // API 96. 경제 용어 등록
    @PostMapping("/api/v1/admin/economic-terms")
    public ResponseEntity<ApiResponse<TermCreatedDTO>> createTerm(@Valid @RequestBody TermCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(economicTermService.create(request)));
    }

    // API 97. 경제 용어 수정
    @PutMapping("/api/v1/admin/economic-terms/{termId}")
    public ResponseEntity<Void> updateTerm(@PathVariable Long termId, @Valid @RequestBody TermUpdateDTO request) {
        economicTermService.update(termId, request);
        return ResponseEntity.noContent().build();
    }

    // API 98. 경제 용어 삭제 (노출 이력이 있으면 비활성)
    @DeleteMapping("/api/v1/admin/economic-terms/{termId}")
    public ResponseEntity<Void> deleteTerm(@PathVariable Long termId) {
        economicTermService.delete(termId);
        return ResponseEntity.noContent().build();
    }

    // API 99. 날짜별 용어 일정 (month=yyyy-MM)
    @GetMapping("/api/v1/admin/term-schedules")
    public ApiResponse<TermScheduleListDTO> getSchedules(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.success(termService.findMonth(month));
    }

    // API 100. 오늘의 용어 지정 (5개, 지난 날짜는 변경 불가)
    @PutMapping("/api/v1/admin/term-schedules/{date}")
    public ResponseEntity<Void> assignSchedule(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody TermScheduleUpdateDTO request) {
        termService.assign(date, request.economicTermIds());
        return ResponseEntity.noContent().build();
    }

    // API 100-1. 날짜 지정 해제 (자동 선정으로 되돌리기, 지난 날짜는 변경 불가)
    @DeleteMapping("/api/v1/admin/term-schedules/{date}")
    public ResponseEntity<Void> clearSchedule(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        termService.clear(date);
        return ResponseEntity.noContent().build();
    }
}
