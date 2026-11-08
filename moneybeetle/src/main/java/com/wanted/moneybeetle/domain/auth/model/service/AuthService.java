package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public AuthService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = normalizeEmail(email);

        Member member = memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        MemberErrorCode.LOGIN_FAILED.getMessage()));

        if (Member.STATUS_WITHDRAWN.equals(member.getStatus())) {
            throw new DisabledException(MemberErrorCode.ACCOUNT_WITHDRAWN.getCode());
        }

        if (Member.STATUS_SUSPENDED.equals(member.getStatus())) {
            throw new DisabledException(MemberErrorCode.ACCOUNT_SUSPENDED.getCode());
        }

        if (member.isLoginLocked(LocalDateTime.now())) {
            throw new LockedException(MemberErrorCode.LOGIN_LOCKED.getCode());
        }

        return new AuthDetails(member);
    }

    /** 비밀번호 인증 실패 시 실패 횟수를 기록하고 잠금 여부를 반환한다. */
    @Transactional
    public boolean recordLoginFailure(String email) {
        return memberRepository.findByEmail(normalizeEmail(email))
                .map(member -> {
                    LocalDateTime now = LocalDateTime.now();
                    member.recordLoginFailure(now);
                    return member.isLoginLocked(now);
                })
                .orElse(false);
    }

    /** 로그인 성공 시 실패 횟수와 잠금을 초기화하고 마지막 로그인 시간을 기록한다. */
    @Transactional
    public void recordLoginSuccess(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        MemberErrorCode.MEMBER_NOT_FOUND.getMessage()));

        member.recordLoginSuccess(LocalDateTime.now());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
