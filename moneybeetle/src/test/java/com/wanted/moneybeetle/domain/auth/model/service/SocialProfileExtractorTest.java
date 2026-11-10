package com.wanted.moneybeetle.domain.auth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   SocialProfileExtractor (이슈 #9) - 제공자가 돌려준 값에서 필요한 것만 꺼내고, 확인되지 않은 이메일은 막는다. 서버 없이 돈다
*  */
class SocialProfileExtractorTest {

    private static Map<String, Object> googleAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "1100223344");
        attributes.put("email", "minji@gmail.com");
        attributes.put("email_verified", true);
        attributes.put("name", "민지");
        return attributes;
    }

    private static Map<String, Object> kakaoAttributes() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "민지");
        Map<String, Object> account = new HashMap<>();
        account.put("email", "minji@kakao.com");
        account.put("is_email_valid", true);
        account.put("is_email_verified", true);
        account.put("profile", profile);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 3456789012L);   // 카카오 사용자 ID 는 숫자다
        attributes.put("kakao_account", account);
        return attributes;
    }

    private static void assertOauthFailed(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.OAUTH_FAILED));
    }

    @Test
    @DisplayName("구글: sub·email·name 을 꺼낸다")
    void google() {
        SocialProfile profile = SocialProfileExtractor.extract("google", googleAttributes());

        assertThat(profile.provider()).isEqualTo("GOOGLE");
        assertThat(profile.providerUserId()).isEqualTo("1100223344");
        assertThat(profile.email()).isEqualTo("minji@gmail.com");
        assertThat(profile.nickname()).isEqualTo("민지");
    }

    @Test
    @DisplayName("구글: 이메일이 확인되지 않았거나 없거나 사용자 ID 가 없으면 OAUTH_FAILED")
    void googleRejectsUnverified() {
        Map<String, Object> unverified = googleAttributes();
        unverified.put("email_verified", false);
        assertOauthFailed(() -> SocialProfileExtractor.extract("google", unverified));

        Map<String, Object> noEmail = googleAttributes();
        noEmail.remove("email");
        assertOauthFailed(() -> SocialProfileExtractor.extract("google", noEmail));

        Map<String, Object> noId = googleAttributes();
        noId.remove("sub");
        assertOauthFailed(() -> SocialProfileExtractor.extract("google", noId));
    }

    @Test
    @DisplayName("카카오: 숫자 id 를 문자열로, kakao_account 의 email·nickname 을 꺼낸다")
    void kakao() {
        SocialProfile profile = SocialProfileExtractor.extract("kakao", kakaoAttributes());

        assertThat(profile.provider()).isEqualTo("KAKAO");
        assertThat(profile.providerUserId()).isEqualTo("3456789012");
        assertThat(profile.email()).isEqualTo("minji@kakao.com");
        assertThat(profile.nickname()).isEqualTo("민지");
    }

    @Test
    @DisplayName("카카오: 닉네임이 없어도 로그인은 된다 (닉네임은 가입 화면에서 사용자가 정한다)")
    void kakaoWithoutNickname() {
        Map<String, Object> attributes = kakaoAttributes();
        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        account.remove("profile");

        assertThat(SocialProfileExtractor.extract("kakao", attributes).nickname()).isNull();
    }

    @Test
    @DisplayName("카카오: 이메일이 확인·유효하지 않거나 없으면 OAUTH_FAILED")
    void kakaoRejectsUnverified() {
        for (String flag : new String[] {"is_email_verified", "is_email_valid"}) {
            Map<String, Object> attributes = kakaoAttributes();
            @SuppressWarnings("unchecked")
            Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
            account.put(flag, false);
            assertOauthFailed(() -> SocialProfileExtractor.extract("kakao", attributes));
        }

        Map<String, Object> noEmail = kakaoAttributes();
        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) noEmail.get("kakao_account");
        account.remove("email");
        assertOauthFailed(() -> SocialProfileExtractor.extract("kakao", noEmail));

        Map<String, Object> noAccount = kakaoAttributes();
        noAccount.remove("kakao_account");
        assertOauthFailed(() -> SocialProfileExtractor.extract("kakao", noAccount));
    }

    @Test
    @DisplayName("알 수 없는 제공자나 null 은 OAUTH_FAILED")
    void unknownProvider() {
        assertOauthFailed(() -> SocialProfileExtractor.extract("naver", googleAttributes()));
        assertOauthFailed(() -> SocialProfileExtractor.extract(null, googleAttributes()));
        assertOauthFailed(() -> SocialProfileExtractor.extract("google", null));
    }
}
