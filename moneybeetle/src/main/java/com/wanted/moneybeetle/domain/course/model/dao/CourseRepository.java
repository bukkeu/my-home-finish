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

    /* 카테고리 삭제 판단 (API 88): 삭제된 강의도 FK로 연결돼 있어서 포함해 센다 */
    boolean existsByCategoryId(Long categoryId);

    /* 관리자 카테고리 트리 (API 85)의 강의 수 (삭제 제외). 카테고리마다 세지 않고 한 번에 */
    @Query("""
            select c.categoryId as categoryId, count(c) as courseCount
            from Course c
            where c.deletedAt is null
            group by c.categoryId
            """)
    List<CategoryCourseCount> countCoursesByCategory();

    interface CategoryCourseCount {
        Long getCategoryId();
        long getCourseCount();
    }

    /* 강사 내 강의 목록 (API 72): 삭제 제외, status가 null이면 전체, 최근 만든 순 */
    @Query("""
            select c from Course c
            where c.instructorId = :instructorId
              and c.deletedAt is null
              and (:status is null or c.status = :status)
            order by c.courseId desc
            """)
    List<Course> findMyCourses(@Param("instructorId") Long instructorId, @Param("status") String status);
}
