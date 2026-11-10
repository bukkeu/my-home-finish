package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.SocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 social_account
*  */
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
