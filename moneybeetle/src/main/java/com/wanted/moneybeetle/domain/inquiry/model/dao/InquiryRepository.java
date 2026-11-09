package com.wanted.moneybeetle.domain.inquiry.model.dao;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (문의)
 *   테이블 inquiry
 *  */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByInquiryIdAndDeletedAtIsNull(Long inquiryId);

    /* 강사 문의함 (API 82, TCH-08): 내 강의의 문의. 답변 대기 글 먼저, 그다음 최신순
     * - 삭제된 글과 관리자가 숨긴 글은 뺀다 (숨긴 글은 관리자만 본다, DP-22)
     * - courseIds가 비면 IN ()이 되지 않도록 서비스에서 조회하지 않는다 */
    @Query(value = """
            select i from Inquiry i
            where i.deletedAt is null
              and i.isHidden = false
              and i.courseId in :courseIds
              and (:status is null or i.status = :status)
            order by case when i.status = :waiting then 0 else 1 end, i.createdAt desc, i.inquiryId desc
            """,
            countQuery = """
            select count(i) from Inquiry i
            where i.deletedAt is null
              and i.isHidden = false
              and i.courseId in :courseIds
              and (:status is null or i.status = :status)
            """)
    Page<Inquiry> searchForInstructor(@Param("courseIds") Collection<Long> courseIds,
                                      @Param("status") String status,
                                      @Param("waiting") String waiting,
                                      Pageable pageable);

    /* 강사 문의함의 답변 대기 수 (삭제·숨김 제외) */
    long countByCourseIdInAndStatusAndDeletedAtIsNullAndIsHiddenFalse(Collection<Long> courseIds, String status);

    /* 하루 작성 수 (DP-30). 지운 글도 센다: 썼다 지웠다 반복해서 한도를 피하지 못하게 */
    long countByMemberIdAndCreatedAtGreaterThanEqual(Long memberId, LocalDateTime from);

    /* 문의 목록 (SP-40)
     * - courseId가 있으면 그 강의 문의, 없으면 사이트 문의
     * - 관리자는 전체, 그 외는 내가 쓴 글 + 내가 강사인 강의의 글
     * - instructorCourseIds가 비면 IN ()이 되지 않도록 서비스에서 -1을 넣어 보낸다 */
    @Query(value = """
            select i from Inquiry i
            where i.deletedAt is null
              and ((:courseId is null and i.courseId is null) or i.courseId = :courseId)
              and (:status is null or i.status = :status)
              and (:admin = true or i.memberId = :memberId or i.courseId in :instructorCourseIds)
            order by i.createdAt desc, i.inquiryId desc
            """,
            countQuery = """
            select count(i) from Inquiry i
            where i.deletedAt is null
              and ((:courseId is null and i.courseId is null) or i.courseId = :courseId)
              and (:status is null or i.status = :status)
              and (:admin = true or i.memberId = :memberId or i.courseId in :instructorCourseIds)
            """)
    Page<Inquiry> search(@Param("courseId") Long courseId,
                         @Param("status") String status,
                         @Param("admin") boolean admin,
                         @Param("memberId") Long memberId,
                         @Param("instructorCourseIds") List<Long> instructorCourseIds,
                         Pageable pageable);
}