package com.wanted.moneybeetle.domain.member.model.service;

import jakarta.servlet.http.HttpSession;

/* comment.
*   담당 : DBA ② (회원·인증)
*   비밀번호가 없는 간편 로그인 회원의 탈퇴 본인 확인 (이슈 #232). 가입한 이메일로 보낸 6자리 코드로 확인한다.
*   제공자(구글)에게 다시 로그인하게 하는 방식은 서버가 "다시 로그인했는가"를 확인할 증거를 받을 수 없어 버렸다.
*   member 가 auth 를 직접 알지 않도록 member 쪽에 둔 인터페이스이고, auth.WithdrawVerificationService 가 구현한다 (참조는 auth -> member 한 방향).
*  */
public interface WithdrawVerifier {

    /** 로그인한 회원의 가입 이메일로 확인 코드를 보낸다. 비밀번호가 있는 회원은 FORBIDDEN, 60초 안에 다시 요청하면 IDENTITY_CODE_TOO_SOON */
    void requestCode(Long memberId, HttpSession session);

    /** 받은 코드가 맞으면 이 세션에서 그 회원의 탈퇴 본인 확인을 마친 것으로 표시한다 (5분 동안 유효, 한 번만 쓸 수 있다) */
    void verify(Long memberId, String code, HttpSession session);

    /**
     * 이 세션에서 그 회원이 5분 안에 코드 확인을 마쳤으면 true 이고 그 확인을 한 번 쓴 것으로 지운다.
     * 확인하지 않았거나 다른 회원이거나 시간이 지났으면 false.
     */
    boolean consumeVerified(Long memberId, HttpSession session);
}
