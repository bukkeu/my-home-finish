package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 terms
*  */
public interface TermsRepository extends JpaRepository<Terms, Long> {

    /** 가입 화면에 보여 줄 약관 목록 (API 1) */
    List<Terms> findAllByOrderByTermsIdAsc();

    /** 필수 약관 전체 (필수 약관 동의 확인용, SP-05) */
    List<Terms> findByRequiredTrue();

    /** 요청에 담긴 약관 ID 가 실제 약관인지 확인할 때 쓴다 */
    List<Terms> findByTermsIdIn(Collection<Long> termsIds);
}
