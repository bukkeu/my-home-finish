package com.wanted.moneybeetle.domain.member.model.service;

import jakarta.servlet.http.HttpSession;

/* comment.
*   담당 : DBA ② (회원·인증)
*   비밀번호가 없는 간편 로그인 회원의 본인 확인 (이슈 #9). 탈퇴처럼 되돌릴 수 없는 요청 전에 구글·카카오로 다시 로그인해 확인했는지 본다.
*   member 가 auth 를 직접 알지 않도록 member 쪽에 둔 인터페이스이고, auth.SocialLoginService 가 구현한다 (참조는 auth -> member 한 방향).
*  */
public interface SocialReauthVerifier {

    /**
     * 이 세션에서 그 회원이 최근(5분 안에) 제공자로 다시 로그인해 본인 확인을 마쳤으면 true 이고 그 확인을 한 번 쓴 것으로 지운다.
     * 확인하지 않았거나 다른 회원이거나 시간이 지났으면 false.
     */
    boolean consumeRecentReauth(Long memberId, HttpSession session);
}
