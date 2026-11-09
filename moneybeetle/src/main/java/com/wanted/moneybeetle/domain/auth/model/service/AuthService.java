package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
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
*   - 잠금·정지·탈퇴 확인은 비밀번호 검사보다 먼저 한다 (DP-10). 없는 이메일은 UsernameNotFoundException 이고,
*     Spring Security 가 이를 BadCredentialsException 으로 바꿔 "이메일 또는 비밀번호"로만 알려 준다 (계정 존재 여부를 숨김)
*   - 정지·탈퇴·잠금은 DisabledException / LockedException 으로 던지고, 오류 코드(ACCOUNT_SUSPENDED 등)를 메시지에 담는다.
*     Spring Security 가 이 예외를 InternalAuthenticationServiceException 으로 감싸므로 AuthFailHandler 가 원인을 풀어서 읽는다
*   - 시간은 공통 Clock(서울)으로 판단한다 (DP-42)
*   - 승인되지 않은 강사는 INSTRUCTOR 가 아니라 MEMBER 권한으로 로그인된다 (SP-07)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = SignupRules.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            throw new UsernameNotFoundException(MemberErrorCode.LOGIN_FAILED.getCode());
        }
        Member member = memberRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException(MemberErrorCode.LOGIN_FAILED.getCode()));

        if (Member.STATUS_WITHDRAWN.equals(member.getStatus())) {
            throw new DisabledException(MemberErrorCode.ACCOUNT_WITHDRAWN.getCode());
        }
        if (Member.STATUS_SUSPENDED.equals(member.getStatus())) {
            throw new DisabledException(MemberErrorCode.ACCOUNT_SUSPENDED.getCode());
        }
        if (member.isLoginLocked(now())) {
            throw new LockedException(MemberErrorCode.LOGIN_LOCKED.getCode());
        }

        return new AuthDetails(member, grantedRole(member));
    }

    /**
     * 비밀번호가 틀렸을 때 실패 횟수를 기록한다. 이번 실패로 계정이 잠겼으면 true.
     * 회원 행을 잠그고 읽어서 같은 계정의 동시 실패가 서로의 횟수를 덮어쓰지 않게 한다. 없는 이메일이면 false.
     */
    @Transactional
    public boolean recordLoginFailure(String email) {
        String normalized = SignupRules.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return memberRepository.findByEmailForUpdate(normalized)
                .map(member -> {
                    LocalDateTime now = now();
                    member.recordLoginFailure(now);
                    return member.isLoginLocked(now);
                })
                .orElse(false);
    }

    /** 로그인 성공 시 실패 횟수와 잠금을 초기화하고 마지막 로그인 시각을 기록한다. */
    @Transactional
    public void recordLoginSuccess(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND.getCode()));
        member.recordLoginSuccess(now());
    }

    /** 회원 테이블의 role 이 INSTRUCTOR 여도 관리자가 승인한 강사일 때만 INSTRUCTOR 권한을 준다 */
    private String grantedRole(Member member) {
        if (!Member.ROLE_INSTRUCTOR.equals(member.getRole())) {
            return member.getRole();
        }
        boolean approved = instructorProfileRepository.findByMemberId(member.getMemberId())
                .map(InstructorProfile::isApproved)
                .orElse(false);
        return approved ? Member.ROLE_INSTRUCTOR : Member.ROLE_MEMBER;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
