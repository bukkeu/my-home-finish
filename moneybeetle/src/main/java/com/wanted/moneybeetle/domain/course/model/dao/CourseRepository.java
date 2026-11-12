package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 course
 *  */
public interface CourseRepository extends JpaRepository<Course, Long> {
    /* 상태를 바꾸는 작업용 조회 (SELECT ... FOR UPDATE).
     * 정보 수정·썸네일·삭제·검수 요청·허가·반려·공개 전환이 같은 강의를 동시에 바꿀 때
     * 앞 작업이 끝날 때까지 기다렸다가 바뀐 상태로 다시 검사하게 한다 (#30) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.courseId = :courseId")
    Optional<Course> findByIdForUpdate(@Param("courseId") Long courseId);

    // 목록: 허가 + 공개 + 삭제 안 됨
    Page<Course> findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(
            List<Long> categoryIds, CourseStatus status, Pageable pageable);

    List<Course> findByStatusAndIsPublicTrueAndDeletedAtIsNull(CourseStatus status);

    // 통합 검색(#15): 허가 + 공개 + 삭제 안 됨 강의 중 제목에 키워드가 들어간 것, 최근 강의부터 limit 개까지 (#296)
    List<Course> findByTitleContainingAndStatusAndIsPublicTrueAndDeletedAtIsNullOrderByCourseIdDesc(
            String keyword, CourseStatus status, Limit limit);

    List<Course> findByCategoryIdInAndStatusAndIsPublicTrueAndDeletedAtIsNull(List<Long> categoryIds, CourseStatus status);

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
    Page<Course> searchForAdmin(@Param("status") CourseStatus status, Pageable pageable);

    /* 대시보드(#33) reviewWaiting: 상태별 강의 수 (삭제 제외) */
    long countByStatusAndDeletedAtIsNull(CourseStatus status);

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
    List<Course> findMyCourses(@Param("instructorId") Long instructorId, @Param("status") CourseStatus status);

    /* 강의 목록·이어서 볼 강의의 영상 수를 강의 여러 개 한 번에 센다 (#87, 강의마다 count 하던 N+1 정리) */
    @Query("""
            select l.courseId as courseId, count(l) as lessons
            from Lesson l
            where l.courseId in :courseIds
            group by l.courseId
            """)
    List<CourseLessonCount> countLessonsByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface CourseLessonCount {
        Long getCourseId();
        long getLessons();
    }

    /* 강사 내 강의 목록(API 72)의 구성 : 강의 여러 개의 영상 수·재생 시간 합을 한 번에 (#369, 강의 수만큼 쿼리가 늘지 않게) */
    @Query("""
            select l.courseId as courseId, count(l) as lessons, coalesce(sum(l.durationSeconds), 0) as totalDurationSeconds
            from Lesson l
            where l.courseId in :courseIds
            group by l.courseId
            """)
    List<CourseLessonStat> findLessonStatsByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface CourseLessonStat {
        Long getCourseId();
        long getLessons();
        long getTotalDurationSeconds();
    }
}
