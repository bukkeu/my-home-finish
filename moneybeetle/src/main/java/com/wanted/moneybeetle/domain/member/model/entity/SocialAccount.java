package com.wanted.moneybeetle.domain.member.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 social_account (구글 간편 로그인 연결, 이슈 #9)
*   - 같은 (provider, provider_user_id) 는 한 번만 연결된다 (DB UNIQUE uk_social_account_1). 회원은 ID 로 참조한다.
*   - provider 는 GOOGLE (대문자). 탈퇴 30일 뒤 Hard Delete (DB설계 탈퇴 보관 규칙)
*  */
@Entity
@Table(name = "social_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    public static final String PROVIDER_GOOGLE = "GOOGLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long socialAccountId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private SocialAccount(Long memberId, String provider, String providerUserId) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialAccount create(Long memberId, String provider, String providerUserId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원이 필요합니다.");
        }
        if (!PROVIDER_GOOGLE.equals(provider)) {
            throw new IllegalArgumentException("provider 는 GOOGLE 이어야 합니다.");
        }
        if (providerUserId == null || providerUserId.isBlank() || providerUserId.length() > 100) {
            throw new IllegalArgumentException("제공자 쪽 사용자 ID 가 올바르지 않습니다.");
        }
        return new SocialAccount(memberId, provider, providerUserId);
    }
}
