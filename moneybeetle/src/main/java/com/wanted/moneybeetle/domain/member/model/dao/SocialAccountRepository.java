package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.SocialAccount;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 social_account
*  */
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** 회원 여러 명의 간편 로그인 연결을 지운다 (탈퇴 회원 개인정보 정리, DP-06) */
    void deleteByMemberIdIn(Collection<Long> memberIds);
}
