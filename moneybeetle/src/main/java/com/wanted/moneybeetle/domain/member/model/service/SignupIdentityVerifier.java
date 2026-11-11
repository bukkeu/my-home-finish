package com.wanted.moneybeetle.domain.member.model.service;

import jakarta.servlet.http.HttpSession;

/* comment.
*   담당 : DBA ② (회원·인증)
*   가입 전 본인인증 확인 (이슈 #9). member 가 auth 를 직접 알지 않도록 member 쪽에 둔 인터페이스이고,
*   auth.IdentityVerificationService 가 구현한다. 그래서 두 도메인의 참조가 auth -> member 한 방향으로만 흐른다.
*   MemberSignupService 가 가입 트랜잭션 안에서 이 인터페이스로 확인하므로 가입·통장 저장과 같은 트랜잭션이라는 동작은 그대로다.
*  */
public interface SignupIdentityVerifier {

    /** 이 세션에서 그 이메일의 본인인증을 마치지 않았으면 IDENTITY_NOT_VERIFIED */
    void requireVerified(String rawEmail, HttpSession session);

    /** 가입이 끝났다. 인증 표시를 지운다 */
    void clear(HttpSession session);
}
