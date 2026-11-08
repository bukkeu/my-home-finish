
package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/* comment.
 *   담당 : SCM (회원·공통)
 *   GET    /api/v1/agreements, /auth/check-email, /auth/check-nickname
 *   POST   /api/v1/auth/signup
 *  */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // API 1. 약관 목록 조회
    @GetMapping("/api/v1/agreements")
    public ApiResponse<List<TermsDTO>> getAgreements() {
        return ApiResponse.success(memberService.findAgreements());
    }

    // API 2. 이메일 중복 확인
    @GetMapping("/api/v1/auth/check-email")
    public ApiResponse<AvailabilityDTO> checkEmail(@RequestParam String email) {
        return ApiResponse.success(memberService.isEmailAvailable(email));
    }

    // API 3. 닉네임 중복 확인
    @GetMapping("/api/v1/auth/check-nickname")
    public ApiResponse<AvailabilityDTO> checkNickname(@RequestParam String nickname) {
        return ApiResponse.success(memberService.isNicknameAvailable(nickname));
    }

    // API 4. 회원가입
    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<ApiResponse<SignupResultDTO>> signup(
            @Valid @RequestBody SignupDTO request) {

        SignupResultDTO result = memberService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(result));
    }
}
