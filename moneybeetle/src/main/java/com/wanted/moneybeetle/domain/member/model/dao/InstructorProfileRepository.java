package com.wanted.moneybeetle.domain.member.model.dao;

import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 instructor_profile
*  */
public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {

    /** 회원의 강사 정보 (member 와 1:1). 강사가 아니면 없다 */
    Optional<InstructorProfile> findByMemberId(Long memberId);

    /** 강의 목록·상세에 보일 강사 이름을 여러 명 한 번에 (InstructorService.findDisplayNames, #52) */
    List<InstructorProfile> findByMemberIdIn(Collection<Long> memberIds);
}
