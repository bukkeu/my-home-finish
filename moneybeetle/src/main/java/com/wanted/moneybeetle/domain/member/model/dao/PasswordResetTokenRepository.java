package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 password_reset_token
*  */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** 재설정 때 토큰 행을 잠그고 읽는다. 같은 토큰으로 동시에 두 번 재설정해도 한 번만 쓰이게 한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** 새 토큰을 발급할 때, 이 회원이 아직 쓰지 않은 이전 토큰을 모두 쓸 수 없게 한다 (가장 최근 메일의 링크만 유효) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.memberId = :memberId and t.usedAt is null")
    int invalidateUnused(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
