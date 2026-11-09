package com.wanted.moneybeetle.domain.inquiry.model.dao;

import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (문의)
 *   테이블 inquiry
 *  */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByInquiryIdAndDeletedAtIsNull(Long inquiryId);

    /* 하루 작성 수 (DP-30). 지운 글도 센다: 썼다 지웠다 반복해서 한도를 피하지 못하게 */
    long countByMemberIdAndCreatedAtGreaterThanEqual(Long memberId, LocalDateTime from);

    /* 대시보드(#33) inquiryWaiting: 상태별 문의 수 (삭제 제외) */
    long countByStatusAndDeletedAtIsNull(String status);

    /* 문의 목록 (API 47, SP-40)
     * - courseId가 있으면 그 강의 문의, 없으면 사이트 문의
     * - 관리자는 전체, 그 외는 내가 쓴 글 + 내가 강사인 강의의 글
     * - 관리자가 아니면 숨긴 글은 목록에서 뺀다 (#38 완료 조건. 상세는 안내 문구로 표시)
     * - instructorCourseIds가 비면 IN ()이 되지 않도록 서비스에서 -1을 넣어 보낸다 */
    @Query(value = """
            select i from Inquiry i
            where i.deletedAt is null
              and ((:courseId is null and i.courseId is null) or i.courseId = :courseId)
              and (:status is null or i.status = :status)
              and (:admin = true or i.memberId = :memberId or i.courseId in :instructorCourseIds)
              and (:admin = true or i.isHidden = false)
            order by i.createdAt desc, i.inquiryId desc
            """,
            countQuery = """
            select count(i) from Inquiry i
            where i.deletedAt is null
              and ((:courseId is null and i.courseId is null) or i.courseId = :courseId)
              and (:status is null or i.status = :status)
              and (:admin = true or i.memberId = :memberId or i.courseId in :instructorCourseIds)
              and (:admin = true or i.isHidden = false)
            """)
    Page<Inquiry> search(@Param("courseId") Long courseId,
                         @Param("status") String status,
                         @Param("admin") boolean admin,
                         @Param("memberId") Long memberId,
                         @Param("instructorCourseIds") List<Long> instructorCourseIds,
                         Pageable pageable);

    /* 관리자 문의 게시판 (API 106). 모든 필터는 null이면 거르지 않는다
     * - courseId: 그 강의 문의만 / hidden: 숨김 여부 / deleted: true면 삭제된 글만, false면 삭제 안 된 글만 */
    @Query(value = """
            select i from Inquiry i
            where (:courseId is null or i.courseId = :courseId)
              and (:status is null or i.status = :status)
              and (:hidden is null or i.isHidden = :hidden)
              and (:deleted is null
                   or (:deleted = true and i.deletedAt is not null)
                   or (:deleted = false and i.deletedAt is null))
            order by i.createdAt desc, i.inquiryId desc
            """,
            countQuery = """
            select count(i) from Inquiry i
            where (:courseId is null or i.courseId = :courseId)
              and (:status is null or i.status = :status)
              and (:hidden is null or i.isHidden = :hidden)
              and (:deleted is null
                   or (:deleted = true and i.deletedAt is not null)
                   or (:deleted = false and i.deletedAt is null))
            """)
    Page<Inquiry> searchForAdmin(@Param("courseId") Long courseId,
                                 @Param("status") String status,
                                 @Param("hidden") Boolean hidden,
                                 @Param("deleted") Boolean deleted,
                                 Pageable pageable);
}