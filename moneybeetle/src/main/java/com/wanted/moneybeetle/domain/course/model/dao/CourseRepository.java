package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 course
 *  */
public interface CourseRepository extends JpaRepository<Course, Long> {
    // 목록: 허가 + 공개 + 삭제 안 됨
    Page<Course> findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(
            List<Long> categoryIds, String status, Pageable pageable);

    List<Course> findByStatusAndIsPublicTrueAndDeletedAtIsNull(String status);

    // 통합 검색(#15): 허가 + 공개 + 삭제 안 됨 강의 중 제목에 키워드가 들어간 것, 최근 강의부터
    List<Course> findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
            String keyword, String status);

    List<Course> findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(List<Long> categoryIds, String status);

    /* 문의(#22): 강사가 맡은 강의 (삭제된 강의 제외) */
    List<Course> findByInstructorIdAndDeletedAtIsNull(Long instructorId);

    /* 관리자 강의 목록 (API 89). status가 null이면 전체. 삭제된 강의 제외, 검수 요청 최신순 */
    @Query(value = """
            select c from Course c
            where c.deletedAt is null
              and (:status is null or c.status = :status)
            order by c.submittedAt desc, c.courseId desc
            """,
            countQuery = """
            select count(c) from Course c
            where c.deletedAt is null
              and (:status is null or c.status = :status)
            """)
    Page<Course> searchForAdmin(@Param("status") String status, Pageable pageable);

    /* 대시보드(#33) reviewWaiting: 상태별 강의 수 (삭제 제외) */
    long countByStatusAndDeletedAtIsNull(String status);
}