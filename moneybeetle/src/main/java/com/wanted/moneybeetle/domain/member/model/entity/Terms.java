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
*   테이블 terms (약관) - 필수 약관에 모두 동의해야 가입할 수 있다 (SP-05)
*  */
@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long termsId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_required")
    private boolean required;

    @Column(nullable = false, length = 20)
    private String version;

    /** 시행 일시 */
    private LocalDateTime effectiveAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private Terms(String title, String content, boolean required, String version, LocalDateTime effectiveAt) {
        this.title = title;
        this.content = content;
        this.required = required;
        this.version = version;
        this.effectiveAt = effectiveAt;
    }

    public static Terms create(String title, String content, boolean required, String version,
                               LocalDateTime effectiveAt) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("약관 이름과 본문이 필요합니다.");
        }
        return new Terms(title, content, required, version, effectiveAt);
    }
}
