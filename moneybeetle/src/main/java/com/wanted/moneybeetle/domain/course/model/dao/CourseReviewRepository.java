package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 course_review
 *  */
public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    /* 검수 상세의 이력: 최신순, 같은 시각이면 ID로 순서 고정 */
    List<CourseReview> findByCourseIdOrderByCreatedAtDescCourseReviewIdDesc(Long courseId);

    /* 강사 화면(API 79)에 보여 줄 가장 최근 반려 사유 */
    Optional<CourseReview> findFirstByCourseIdAndResultOrderByCreatedAtDescCourseReviewIdDesc(Long courseId, String result);

    /* 강사 내 강의 목록 (API 72)의 반려 사유: 강의 여러 개를 한 번에 최신순으로 (서비스가 강의별 첫 건만 쓴다) */
    List<CourseReview> findByCourseIdInAndResultOrderByCreatedAtDescCourseReviewIdDesc(Collection<Long> courseIds,
                                                                                       String result);
}