package com.wanted.moneybeetle.domain.auth.model.dto;

import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/* comment.
*   담당 : DBA ② (회원·인증)
*   UserDetails 구현체 - 세션에 담길 로그인 회원 정보 (memberId, email, nickname, role)
*   - role 은 실제로 부여된 권한이다. 승인되지 않은 강사는 INSTRUCTOR 가 아니라 MEMBER 로 들어온다 (SP-07)
*   - 권한 이름은 ROLE_ 접두어 없이 MEMBER / INSTRUCTOR / ADMIN 그대로다 (hasAuthority 로 검사)
*   - 정지·탈퇴·잠금 계정은 AuthService 가 여기까지 오기 전에 예외로 막는다
*  */
@Getter
public class AuthDetails implements UserDetails {

    private final Long memberId;
    private final String email;
    private final String password;
    private final String nickname;
    private final String role;

    /**
     * 권한은 LoginMemberDTO.grantedRole 이다. 회원 테이블의 role 이 INSTRUCTOR 여도 승인 전이면 MEMBER 다
     */
    public AuthDetails(LoginMemberDTO member) {
        this.memberId = member.getMemberId();
        this.email = member.getEmail();
        this.password = member.getPasswordHash();
        this.nickname = member.getNickname();
        this.role = member.getGrantedRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
