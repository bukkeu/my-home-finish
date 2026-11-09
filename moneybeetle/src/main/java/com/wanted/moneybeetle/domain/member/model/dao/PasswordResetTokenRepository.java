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

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 새 토큰의 메일이 발송 대기열에 들어간 뒤, 이 회원의 쓰지 않은 이전 토큰을 모두 쓸 수 없게 한다 (가장 최근 메일의 링크만 유효). 새 토큰은 제외한다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.memberId = :memberId and t.usedAt is null and t.tokenHash <> :keepTokenHash")
    int invalidateUnusedExcept(@Param("memberId") Long memberId, @Param("keepTokenHash") String keepTokenHash,
                               @Param("now") LocalDateTime now);

    /** 메일을 보내지 못한 새 토큰을 지운다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetToken t where t.tokenHash = :tokenHash and t.usedAt is null")
    int deleteUnusedByTokenHash(@Param("tokenHash") String tokenHash);
}
