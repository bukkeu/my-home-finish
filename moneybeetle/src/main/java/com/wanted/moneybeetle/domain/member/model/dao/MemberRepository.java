package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 member
*   이메일은 소문자로 저장하므로 조회할 때도 소문자로 바꿔서 넘긴다 (MemberService 가 처리한다).
*  */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일 중복 확인 (SP-01). 넘기는 값은 이미 소문자여야 한다 */
    boolean existsByEmail(String email);

    /** 닉네임 중복 확인 (SP-02) */
    boolean existsByNickname(String nickname);

    /** 로그인용 조회 (AuthService, #6). 넘기는 값은 이미 소문자여야 한다 */
    Optional<Member> findByEmail(String email);
}
