package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.dto.InstructorApplicationDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/* comment.
*   담당 : DBA ① (사용자·강사 관리, #37)
*   관리자 회원·강사 관리 조회 전용 (API 101·104). MemberRepository(DBA ②)와 따로 둬서 회원가입·로그인 작업과 충돌하지 않게 한다
*   - 목록 필터(role, status)는 idx_member_1 (role, status) 을 쓴다 (#87)
*  */
public interface AdminMemberRepository extends Repository<Member, Long> {

    /* API 101 사용자·강사 목록: 관리자 계정은 빼고, 역할·상태·키워드(닉네임·이메일) 모두 선택. 최근 가입 순
     * keyword 는 서비스가 LIKE 특수문자(! % _)를 '!'로 이스케이프해서 넘긴다 → 글자 그대로 검색 */
    @Query(value = """
            select m from Member m
            where m.role <> 'ADMIN'
              and (:role is null or m.role = :role)
              and (:status is null or m.status = :status)
              and (:keyword is null or m.nickname like concat('%', :keyword, '%') escape '!'
                   or m.email like concat('%', :keyword, '%') escape '!')
            order by m.memberId desc
            """,
            countQuery = """
            select count(m) from Member m
            where m.role <> 'ADMIN'
              and (:role is null or m.role = :role)
              and (:status is null or m.status = :status)
              and (:keyword is null or m.nickname like concat('%', :keyword, '%') escape '!'
                   or m.email like concat('%', :keyword, '%') escape '!')
            """)
    Page<Member> search(@Param("role") String role, @Param("status") String status,
                        @Param("keyword") String keyword, Pageable pageable);

    /* API 104 강사 가입 신청 목록: 실제로 신청한(applied_at 있는) 강사만, 승인 상태별(기본 PENDING), 탈퇴 회원 제외, 신청 순.
     * 관리자가 직접 권한을 준 강사(API 103)는 신청이 아니라서 빠진다 */
    @Query(value = """
            select new com.wanted.moneybeetle.domain.member.model.dto.InstructorApplicationDTO(
                       p.memberId, p.displayName, p.field, m.email, p.approvalStatus, p.appliedAt)
            from InstructorProfile p, Member m
            where p.memberId = m.memberId
              and p.approvalStatus = :status
              and p.appliedAt is not null
              and m.status <> 'WITHDRAWN'
            order by p.appliedAt asc, p.instructorProfileId asc
            """,
            countQuery = """
            select count(p) from InstructorProfile p, Member m
            where p.memberId = m.memberId
              and p.approvalStatus = :status
              and p.appliedAt is not null
              and m.status <> 'WITHDRAWN'
            """)
    Page<InstructorApplicationDTO> findApplications(@Param("status") String status, Pageable pageable);
}
