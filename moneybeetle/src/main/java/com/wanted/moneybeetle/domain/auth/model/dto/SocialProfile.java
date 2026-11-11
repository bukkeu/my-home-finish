package com.wanted.moneybeetle.domain.auth.model.dto;

import java.io.Serializable;

/* comment.
*   담당 : DBA ② (회원·인증)
*   간편 로그인(구글)이 알려 준 사용자 정보 (이슈 #9). 처음 온 사람이면 가입을 마칠 때까지 HTTP 세션에 담아 둔다.
*   - provider : GOOGLE, providerUserId : 제공자 쪽 사용자 ID, email : 제공자가 확인한 이메일, nickname : 제공자가 준 이름(가입 화면이 미리 채우는 용도)
*   - 이메일·이름만 받는다 (DP-13). 그 밖의 정보는 요청하지 않는다.
*  */
public record SocialProfile(String provider, String providerUserId, String email, String nickname) implements Serializable {

    private static final long serialVersionUID = 1L;
}
