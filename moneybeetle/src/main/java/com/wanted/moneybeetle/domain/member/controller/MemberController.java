
package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MyInfoDTO;
import com.wanted.moneybeetle.domain.member.model.dto.ProfileImageDTO;
import com.wanted.moneybeetle.domain.member.model.dto.PublicProfileDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsListDTO;
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

/* comment.
*   담당 : DBA ② (회원·인증)
*   GET    /api/v1/agreements, /auth/check-email, /auth/check-nickname
*   POST   /api/v1/auth/signup, /auth/instructor-signup
*   GET    /api/v1/members/me, /members/{memberId}/profile
*   PATCH  /api/v1/members/me, /members/me/password
*   POST   /api/v1/members/me/profile-image
*   DELETE /api/v1/members/me (탈퇴)
*   화면 : /signup, /instructor/signup, /user/my-page(설정·탈퇴 탭)
*  */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberProfileService memberProfileService;

    // API 1. 약관 목록 조회
    @GetMapping("/api/v1/agreements")
    public ApiResponse<TermsListDTO> getAgreements() {
        // API 명세서 1의 응답은 { "agreements": [ ... ] } 이다
        return ApiResponse.success(new TermsListDTO(memberService.findAgreements()));
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

    // API 13. 내 정보 (메뉴 프로필)
    @GetMapping("/api/v1/members/me")
    public ApiResponse<MyInfoDTO> getMyInfo(@AuthenticationPrincipal AuthDetails me) {
        return ApiResponse.success(memberProfileService.getMyInfo(me.getMemberId()));
    }

    // API 14. 설정 수정
    @PatchMapping("/api/v1/members/me")
    public ApiResponse<MemberUpdateResultDTO> updateMyInfo(
            @AuthenticationPrincipal AuthDetails me,
            @Valid @RequestBody MemberUpdateDTO request) {
        return ApiResponse.success(memberService.updateProfile(me.getMemberId(), request));
    }

    // API 15. 프로필 이미지 변경
    @PostMapping("/api/v1/members/me/profile-image")
    public ApiResponse<ProfileImageDTO> changeProfileImage(
            @AuthenticationPrincipal AuthDetails me,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(memberProfileService.changeProfileImage(me.getMemberId(), file));
    }

    // API 18. 다른 회원 공개 프로필
    @GetMapping("/api/v1/members/{memberId}/profile")
    public ApiResponse<PublicProfileDTO> getPublicProfile(@PathVariable Long memberId) {
        return ApiResponse.success(memberProfileService.getPublicProfile(memberId));
    }
}
