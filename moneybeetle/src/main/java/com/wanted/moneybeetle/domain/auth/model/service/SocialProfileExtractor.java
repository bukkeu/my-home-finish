package com.wanted.moneybeetle.domain.auth.model.service;

import com.wanted.moneybeetle.domain.auth.model.dto.SocialProfile;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.entity.SocialAccount;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.Map;

/* comment.
*   담당 : DBA ② (회원·인증)
*   제공자가 돌려준 사용자 정보(attributes)에서 필요한 값만 꺼낸다 (이슈 #9, DP-13). 서버·네트워크 없이 값만 보고 판단한다.
*   - 구글: sub(사용자 ID), email, email_verified, name
*   - 카카오: id(사용자 ID), kakao_account.email, kakao_account.is_email_valid·is_email_verified, kakao_account.profile.nickname
*   - **제공자가 확인한 이메일만 믿는다.** 이메일이 없거나 확인되지 않았으면 가입·로그인을 막는다(OAUTH_FAILED).
*     확인되지 않은 이메일을 받아 주면 남의 이메일로 먼저 가입해 그 사람의 가입을 막을 수 있기 때문이다.
*  */
public final class SocialProfileExtractor {

    public static final String REGISTRATION_GOOGLE = "google";
    public static final String REGISTRATION_KAKAO = "kakao";

    private SocialProfileExtractor() {
    }

    /** registrationId 는 소문자(google / kakao). 알 수 없는 제공자나 필요한 값이 없으면 OAUTH_FAILED */
    public static SocialProfile extract(String registrationId, Map<String, Object> attributes) {
        if (attributes == null || registrationId == null) {
            throw failed();
        }
        return switch (registrationId) {
            case REGISTRATION_GOOGLE -> google(attributes);
            case REGISTRATION_KAKAO -> kakao(attributes);
            default -> throw failed();
        };
    }

    private static SocialProfile google(Map<String, Object> attributes) {
        String userId = text(attributes.get("sub"));
        String email = text(attributes.get("email"));
        if (userId == null || email == null || !isTrue(attributes.get("email_verified"))) {
            throw failed();
        }
        return new SocialProfile(SocialAccount.PROVIDER_GOOGLE, userId, email, text(attributes.get("name")));
    }

    @SuppressWarnings("unchecked")
    private static SocialProfile kakao(Map<String, Object> attributes) {
        String userId = text(attributes.get("id"));
        if (userId == null || !(attributes.get("kakao_account") instanceof Map<?, ?> rawAccount)) {
            throw failed();
        }
        Map<String, Object> account = (Map<String, Object>) rawAccount;
        String email = text(account.get("email"));
        if (email == null || !isTrue(account.get("is_email_valid")) || !isTrue(account.get("is_email_verified"))) {
            throw failed();
        }
        String nickname = null;
        if (account.get("profile") instanceof Map<?, ?> profile) {
            nickname = text(profile.get("nickname"));
        }
        return new SocialProfile(SocialAccount.PROVIDER_KAKAO, userId, email, nickname);
    }

    /** 값이 없거나 비어 있으면 null, 있으면 앞뒤 공백을 지운 문자열 (카카오 id 는 숫자라 문자열로 바꾼다) */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static BusinessException failed() {
        return new BusinessException(MemberErrorCode.OAUTH_FAILED);
    }
}
