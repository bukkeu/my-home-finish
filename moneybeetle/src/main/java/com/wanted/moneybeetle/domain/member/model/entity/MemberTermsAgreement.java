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
*   테이블 member_terms_agreement (약관 동의) - 한 회원이 같은 약관에 한 번만 기록된다 (UNIQUE member_id + terms_id)
*  */
@Entity
@Table(name = "member_terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberTermsAgreementId;

    /** 다른 도메인이 아니라 같은 member 도메인이지만, 연관관계 대신 ID 로 참조한다 (수업 미니 프로젝트 방식) */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long termsId;

    @Column(name = "is_agreed")
    private boolean agreed;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime agreedAt;

    private MemberTermsAgreement(Long memberId, Long termsId, boolean agreed) {
        this.memberId = memberId;
        this.termsId = termsId;
        this.agreed = agreed;
    }

    public static MemberTermsAgreement create(Long memberId, Long termsId, boolean agreed) {
        if (memberId == null || termsId == null) {
            throw new IllegalArgumentException("회원과 약관이 필요합니다.");
        }
        return new MemberTermsAgreement(memberId, termsId, agreed);
    }
}
