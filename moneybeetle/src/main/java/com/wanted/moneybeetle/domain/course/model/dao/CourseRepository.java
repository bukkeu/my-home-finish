package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
    List<Course> findByInstructorIdAndDeletedAtIsNull(Long instructorId);
}
