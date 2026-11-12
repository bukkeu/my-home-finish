package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
import java.time.LocalDateTime;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그인 회원 조회용 (memberId, email, passwordHash, nickname, grantedRole, status, lockedUntil)
*   - auth 도메인(AuthService)이 member 의 Entity·Repository 를 직접 쓰지 않고 MemberService 로 받는 값이다
*   - grantedRole 은 실제로 부여할 권한이다. 회원 테이블의 role 이 INSTRUCTOR 여도 관리자가 승인하기 전에는 MEMBER 다 (SP-07)
*  */
@Getter
public class LoginMemberDTO {

    private final Long memberId;
    private final String email;
    private final String passwordHash;
    private final String nickname;
    private final String grantedRole;
    private final String status;
    private final LocalDateTime lockedUntil;

    private LoginMemberDTO(Long memberId, String email, String passwordHash, String nickname,
                           String grantedRole, String status, LocalDateTime lockedUntil) {
        this.memberId = memberId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.grantedRole = grantedRole;
        this.status = status;
        this.lockedUntil = lockedUntil;
    }

    public static LoginMemberDTO from(Member member, String grantedRole) {
        return new LoginMemberDTO(member.getMemberId(), member.getEmail(), member.getPasswordHash(),
                member.getNickname(), grantedRole, member.getStatus(), member.getLockedUntil());
    }

    public boolean isWithdrawn() {
        return MemberStatus.WITHDRAWN.name().equals(status);
    }

    public boolean isSuspended() {
        return MemberStatus.SUSPENDED.name().equals(status);
    }

    /** 지금 로그인 잠금 중인가 */
    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }
}
