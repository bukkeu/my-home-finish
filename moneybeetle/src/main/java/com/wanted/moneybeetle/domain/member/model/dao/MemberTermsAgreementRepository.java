package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.MemberTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 member_terms_agreement
*  */
public interface MemberTermsAgreementRepository extends JpaRepository<MemberTermsAgreement, Long> {

    boolean existsByMemberIdAndTermsId(Long memberId, Long termsId);
}
