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
*   테이블 password_reset_token (USR-09, DP-12)
*   - 메일로 보낸 토큰 원문은 저장하지 않고 해시(SHA-256 16진수)만 저장한다 (DB 가 새어도 토큰을 쓸 수 없게)
*   - 발급 후 10분 동안만 유효하고 한 번만 쓸 수 있다
*  */
@Entity
@Table(name = "password_reset_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    /** 토큰 유효 시간 (DP-12) */
    public static final long VALID_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_token_id")
    private Long passwordResetTokenId;

    private Long memberId;

    @Column(nullable = false, length = 255)
    private String tokenHash;

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private PasswordResetToken(Long memberId, String tokenHash, LocalDateTime expiresAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** 지금(now)부터 10분 동안 쓸 수 있는 토큰을 만든다. now 는 서울 기준 시각을 밖에서 받는다 (DP-42) */
    public static PasswordResetToken issue(Long memberId, String tokenHash, LocalDateTime now) {
        if (memberId == null || tokenHash == null || tokenHash.isBlank() || now == null) {
            throw new IllegalArgumentException("회원, 토큰 해시, 발급 시각이 필요합니다.");
        }
        return new PasswordResetToken(memberId, tokenHash, now.plusMinutes(VALID_MINUTES));
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /** 사용 처리한다. 한 번 쓴 토큰은 다시 쓸 수 없다 */
    public void markUsed(LocalDateTime now) {
        if (isUsed()) {
            throw new IllegalStateException("이미 사용한 토큰입니다.");
        }
        this.usedAt = now;
    }
}
