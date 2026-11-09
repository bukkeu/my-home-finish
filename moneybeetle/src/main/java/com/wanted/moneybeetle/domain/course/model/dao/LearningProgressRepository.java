package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 learning_progress
 *  */
public interface LearningProgressRepository extends JpaRepository<LearningProgress, Long> {

    /* 이 회원이 이 강의를 본 기록 (이어보기 위치) */
    Optional<LearningProgress> findByMemberIdAndCourseId(Long memberId, Long courseId);

    /* 학습 완료 목록: 허가 + 공개 + 삭제 안 된 강의만, 최근 완료 순 (DH-01, SP-31)
     * 페이지 수가 맞도록 DB에서 강의를 조인해 거른다. 기록 자체는 지우지 않는다 */
    @Query(value = """
            select p from LearningProgress p, Course c
            where p.courseId = c.courseId
              and p.memberId = :memberId and p.status = :progressStatus
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            order by p.completedAt desc
            """,
            countQuery = """
            select count(p) from LearningProgress p, Course c
            where p.courseId = c.courseId
              and p.memberId = :memberId and p.status = :progressStatus
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            """)
    Page<LearningProgress> findVisibleByMemberIdAndStatus(@Param("memberId") Long memberId,
                                                          @Param("progressStatus") String progressStatus,
                                                          @Param("courseStatus") String courseStatus,
                                                          Pageable pageable);

    /* 홈 '이어서 볼 강의' (최근에 본 순서 5개) */
    List<LearningProgress> findTop5ByMemberIdAndStatusOrderByUpdatedAtDesc(Long memberId, String status);

    /* 학습 기록이 하나라도 있는지 (홈 vr1/vr2 분기) */
    boolean existsByMemberId(Long memberId);

    /* 강의 삭제 가능 여부 (SP-32): 한 명이라도 학습 기록이 있으면 삭제 불가 */
    boolean existsByCourseId(Long courseId);

    /* 관리자 강의 목록의 수강생 수. 강의 여러 개를 한 번에 세서 N+1을 막는다 */
    @Query("""
            select lp.courseId as courseId, count(lp) as learners
            from LearningProgress lp
            where lp.courseId in :courseIds
            group by lp.courseId
            """)
    List<CourseLearnerCount> countLearnersByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    interface CourseLearnerCount {
        Long getCourseId();
        long getLearners();
    }
}