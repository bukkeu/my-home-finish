package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 member
*   이메일은 소문자로 저장하므로 조회할 때도 소문자로 바꿔서 넘긴다 (MemberService 가 처리한다).
*  */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일 중복 확인 (SP-01). 넘기는 값은 이미 소문자여야 한다 */
    boolean existsByEmail(String email);

    /** 역할별 회원 수 (관리자 대시보드, #33) */
    long countByRole(String role);

    /** 역할·상태별 회원 수 (관리자 대시보드, #33) */
    long countByRoleAndStatus(String role, String status);

    /** 닉네임 중복 확인 (SP-02) */
    boolean existsByNickname(String nickname);

    /** 아이디(이메일) 찾기 (API 9, #8). 닉네임은 고유하다 */
    Optional<Member> findByNicknameAndBirthDate(String nickname, LocalDate birthDate);

    /** 로그인용 조회 (AuthService, #6). 넘기는 값은 이미 소문자여야 한다 */
    Optional<Member> findByEmail(String email);

    /**
     * 로그인 실패 기록용 조회. 회원 행을 잠그고 읽어서, 같은 계정으로 동시에 여러 번 틀려도 실패 횟수가 덮어써지지 않게 한다 (DP-10).
     * 넘기는 값은 이미 소문자여야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.email = :email")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);

    /**
     * 잔액·경험치를 바꾸기 위해 회원 행을 잠그고 읽는다 (비관적 락, 트랜잭션이 끝날 때까지 유지).
     * 같은 사람이 두 탭에서 동시에 요청해도 캐시가 음수가 되거나 두 번 지급되지 않게 한다 (SP-15, SP-18, DP-36).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.memberId = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
