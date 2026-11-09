package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증)
*   UserDetailsService 구현 - 이메일(소문자)로 회원 조회, 정지·탈퇴·잠금 확인 (이슈 #6)
*   - 회원 조회·로그인 기록·강사 승인 확인은 member 도메인의 MemberService 에 맡기고, 여기서는 Spring Security 규칙만 다룬다
*     (다른 도메인의 Repository·Entity 를 직접 쓰지 않는다)
*   - 잠금·정지·탈퇴 확인은 비밀번호 검사보다 먼저 한다 (DP-10). 없는 이메일은 UsernameNotFoundException 이고,
*     Spring Security 가 이를 BadCredentialsException 으로 바꿔 "이메일 또는 비밀번호"로만 알려 준다 (계정 존재 여부를 숨김)
*   - 정지·탈퇴·잠금은 DisabledException / LockedException 으로 던지고, 오류 코드(ACCOUNT_SUSPENDED 등)를 메시지에 담는다.
*     Spring Security 가 이 예외를 InternalAuthenticationServiceException 으로 감싸므로 AuthFailHandler 가 원인을 풀어서 읽는다
*   - 시간은 공통 Clock(서울)으로 판단한다 (DP-42)
*   - 승인되지 않은 강사는 INSTRUCTOR 가 아니라 MEMBER 권한으로 로그인된다 (SP-07, MemberService 가 정한다)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private final MemberService memberService;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LoginMemberDTO member = memberService.findLoginMember(email)
                .orElseThrow(() -> new UsernameNotFoundException(MemberErrorCode.LOGIN_FAILED.getCode()));

        if (member.isWithdrawn()) {
            throw new DisabledException(MemberErrorCode.ACCOUNT_WITHDRAWN.getCode());
        }
        if (member.isSuspended()) {
            throw new DisabledException(MemberErrorCode.ACCOUNT_SUSPENDED.getCode());
        }
        if (member.isLocked(now())) {
            throw new LockedException(MemberErrorCode.LOGIN_LOCKED.getCode());
        }

        return new AuthDetails(member);
    }

    /**
     * 비밀번호가 틀렸을 때 실패를 기록한다. 이번 실패로 계정이 잠겼으면 true.
     * 클래스가 읽기 전용이라 여기서 쓰기 트랜잭션으로 다시 연다: MemberService 의 @Transactional 은 이미 열린 트랜잭션에 참여하므로,
     * 읽기 전용으로 열려 있으면 실패 횟수가 저장되지 않는다. 행 잠금은 MemberService 가 건다.
     */
    @Transactional
    public boolean recordLoginFailure(String email) {
        return memberService.recordLoginFailure(email, now());
    }

    /** 로그인 성공 시 실패 횟수와 잠금을 초기화하고 마지막 로그인 시각을 기록한다 (위와 같은 이유로 쓰기 트랜잭션). */
    @Transactional
    public void recordLoginSuccess(Long memberId) {
        memberService.recordLoginSuccess(memberId, now());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
