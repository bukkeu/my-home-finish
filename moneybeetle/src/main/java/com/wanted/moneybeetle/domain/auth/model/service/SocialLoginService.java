package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SocialSignupDTO;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.member.model.service.MemberSignupService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;

/* comment.
*   담당 : DBA ② (회원·인증)
*   간편 로그인(구글·카카오) 처리 (USR-07, DP-13, 이슈 #9)
*   - login : 제공자가 알려 준 정보로 로그인한다.
*       ① 이미 연결된 회원이면 정상(ACTIVE) 계정인지 보고 로그인한다 (탈퇴·정지는 ACCOUNT_WITHDRAWN / ACCOUNT_SUSPENDED).
*       ② 처음이면 같은 이메일로 이미 가입한 사람이 있는지 본다. 있으면 EMAIL_DUPLICATED 로 막는다.
*          이메일이 같다고 자동으로 연결하지 않는다: 이메일·비밀번호로 가입한 계정에 제공자 계정을 몰래 붙이면 계정 탈취 통로가 될 수 있어서,
*          그 이메일로 로그인한 뒤 연결하는 기능이 생기기 전에는 이메일로 로그인하게 안내한다.
*       ③ 이메일이 새것이면 가입 화면이 이어서 받을 수 있게 정보를 세션에 담고 "가입 필요"로 돌려준다.
*   - completeSignup : 세션의 정보와 사용자가 입력한 정보로 가입을 마친다. 이메일은 세션 값만 쓴다(요청으로 받지 않는다).
*     가입이 끝나면 세션의 정보를 지운다. 가입 실패(닉네임 중복 등)에는 지우지 않아 화면에서 고쳐 다시 보낼 수 있다.
*   - 이 클래스는 트랜잭션을 열지 않는다. 로그인 기록·가입은 각자 자기 트랜잭션이다.
*  */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    static final String SESSION_KEY = "socialSignupPending";

    private final MemberService memberService;
    private final MemberSignupService memberSignupService;
    private final AuthService authService;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    /** 간편 로그인을 시작할 수 있는 제공자인가 확인한다. 지원하지 않거나 키가 없어 등록되지 않은 제공자면 OAUTH_PROVIDER_UNAVAILABLE */
    public void requireAvailableProvider(String provider) {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        boolean supported = SocialProfileExtractor.REGISTRATION_GOOGLE.equals(provider)
                || SocialProfileExtractor.REGISTRATION_KAKAO.equals(provider);
        if (repository == null || !supported || repository.findByRegistrationId(provider) == null) {
            throw new BusinessException(MemberErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
    }

    /** 로그인 결과: 로그인된 회원, 또는 가입이 더 필요함(member 가 null) */
    public record Result(LoginMemberDTO member) {
        public boolean signupRequired() {
            return member == null;
        }
    }

    public Result login(SocialProfile profile, HttpSession session) {
        Optional<LoginMemberDTO> linked = memberService.findLoginMemberBySocial(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            LoginMemberDTO member = linked.get();
            if (member.isWithdrawn()) {
                throw new BusinessException(MemberErrorCode.ACCOUNT_WITHDRAWN);
            }
            if (member.isSuspended()) {
                throw new BusinessException(MemberErrorCode.ACCOUNT_SUSPENDED);
            }
            authService.recordLoginSuccess(member.getMemberId());
            session.removeAttribute(SESSION_KEY);
            return new Result(member);
        }

        String email = SignupRules.normalizeEmail(profile.email());
        if (!SignupRules.isValidEmail(profile.email()) || !SignupRules.isValidEmail(email)) {
            throw new BusinessException(MemberErrorCode.OAUTH_FAILED);
        }
        if (!memberService.isEmailAvailable(profile.email()).isAvailable()) {
            throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }
        session.setAttribute(SESSION_KEY, new SocialProfile(profile.provider(), profile.providerUserId(), email, profile.nickname()));
        return new Result(null);
    }

    /** 가입을 이어서 하는 중인 사람의 제공자 정보. 세션에 없으면(만료·처음부터 안 함) OAUTH_SESSION_EXPIRED */
    public SocialProfile getPending(HttpSession session) {
        Object attribute = session == null ? null : session.getAttribute(SESSION_KEY);
        if (attribute instanceof SocialProfile profile) {
            return profile;
        }
        throw new BusinessException(MemberErrorCode.OAUTH_SESSION_EXPIRED);
    }

    /** 가입을 마치고 로그인할 회원 정보를 돌려준다 (API 8-2) */
    public LoginMemberDTO completeSignup(SocialSignupDTO request, HttpSession session) {
        SocialProfile pending = getPending(session);
        LoginMemberDTO member = memberSignupService.signupSocial(
                request, pending.email(), pending.provider(), pending.providerUserId());
        session.removeAttribute(SESSION_KEY);
        authService.recordLoginSuccess(member.getMemberId());
        return member;
    }
}
