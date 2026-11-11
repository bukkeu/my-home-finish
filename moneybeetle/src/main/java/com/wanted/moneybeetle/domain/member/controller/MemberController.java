
package com.wanted.moneybeetle.domain.member.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.PasswordChangeDTO;
import com.wanted.moneybeetle.domain.member.model.dto.WithdrawDTO;
import com.wanted.moneybeetle.domain.member.model.dto.WithdrawVerificationDTO;
import com.wanted.moneybeetle.domain.member.model.service.AccountService;
import com.wanted.moneybeetle.domain.member.model.service.WithdrawVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MyInfoDTO;
import com.wanted.moneybeetle.domain.member.model.dto.ProfileImageDTO;
import com.wanted.moneybeetle.domain.member.model.dto.PublicProfileDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberProfileService;
import com.wanted.moneybeetle.domain.member.model.service.MemberSignupService;
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
*   POST   /api/v1/members/me/withdraw-verification/request, /members/me/withdraw-verification (간편 로그인 회원의 탈퇴 본인 확인, #232)
*   DELETE /api/v1/members/me/withdraw-verification (탈퇴 본인 확인 취소, #279)
*   DELETE /api/v1/members/me (탈퇴)
*   화면 : /signup, /instructor/signup, /user/my-page(설정·탈퇴 탭)
*  */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AccountService accountService;
    private final WithdrawVerifier withdrawVerifier;
    private final MemberProfileService memberProfileService;
    private final MemberSignupService memberSignupService;

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
            @Valid @RequestBody SignupDTO request, HttpSession session) {

        SignupResultDTO result = memberSignupService.signup(request, session);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(result));
    }

    // API 12. 강사 가입 신청 (#30) - 승인 대기(PENDING)로 저장, 관리자가 승인하면 강사 권한. 본인인증을 마친 이메일이어야 한다
    @PostMapping("/api/v1/auth/instructor-signup")
    public ResponseEntity<ApiResponse<InstructorSignupResultDTO>> signupInstructor(
            @Valid @RequestBody InstructorSignupDTO request, HttpSession session) {

        InstructorSignupResultDTO result = memberSignupService.signupInstructor(request, session);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(result));
    }

    // API 16. 비밀번호 변경
    @PatchMapping("/api/v1/members/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthDetails me,
            @Valid @RequestBody PasswordChangeDTO request) {
        accountService.changePassword(me.getMemberId(), request.getCurrentPassword(),
                request.getNewPassword(), request.getNewPasswordConfirm());
        return ResponseEntity.noContent().build();
    }

    // API 17-1. 간편 로그인 회원의 탈퇴 본인 확인 코드 요청 (가입 이메일로 6자리 코드를 보낸다. 비밀번호가 있는 회원은 FORBIDDEN)
    @PostMapping("/api/v1/members/me/withdraw-verification/request")
    public ResponseEntity<Void> requestWithdrawCode(@AuthenticationPrincipal AuthDetails me, HttpServletRequest httpRequest) {
        withdrawVerifier.requestCode(me.getMemberId(), httpRequest.getSession());
        return ResponseEntity.accepted().build();
    }

    // API 17-2. 간편 로그인 회원의 탈퇴 본인 확인 (받은 코드 확인. 확인은 5분 동안 한 번 탈퇴에 쓸 수 있다)
    @PostMapping("/api/v1/members/me/withdraw-verification")
    public ResponseEntity<Void> verifyWithdrawCode(@AuthenticationPrincipal AuthDetails me,
                                                   @Valid @RequestBody WithdrawVerificationDTO request,
                                                   HttpServletRequest httpRequest) {
        withdrawVerifier.verify(me.getMemberId(), request.getCode(), httpRequest.getSession());
        return ResponseEntity.noContent().build();
    }

    // API 17-3. 탈퇴 본인 확인 취소 (세션에 남은 코드·확인 완료 표시를 지운다. 없어도 204, 세션이 없어도 새로 만들지 않는다)
    @DeleteMapping("/api/v1/members/me/withdraw-verification")
    public ResponseEntity<Void> cancelWithdrawVerification(@AuthenticationPrincipal AuthDetails me, HttpServletRequest httpRequest) {
        withdrawVerifier.cancel(me.getMemberId(), httpRequest.getSession(false));
        return ResponseEntity.noContent().build();
    }

    // API 17. 회원 탈퇴 (계정 비활성화 후 이 로그인 세션도 끝낸다)
    @DeleteMapping("/api/v1/members/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal AuthDetails me,
            @RequestBody(required = false) WithdrawDTO request,
            HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        // 비밀번호가 있는 회원은 현재 비밀번호로, 간편 로그인 회원은 가입 이메일로 받은 코드를 확인한 세션(API 17-1·17-2)으로 본인 확인한다
        accountService.withdraw(me.getMemberId(), request == null ? null : request.getPassword(), session);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
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
