package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 lesson_watch (시청 기록)
 *  */
public interface LessonWatchRepository extends JpaRepository<LessonWatch, Long> {

    Optional<LessonWatch> findByMemberIdAndLessonId(Long memberId, Long lessonId);

    /* 시청 기록: 허가 + 공개 + 삭제 안 된 강의의 영상만, 최근 본 순 (DH-01, SP-31)
     * 페이지 수가 맞도록 DB에서 강의를 조인해 거른다. 기록 자체는 지우지 않는다 */
    @Query(value = """
            select w from LessonWatch w, Lesson l, Course c
            where w.lessonId = l.lessonId and l.courseId = c.courseId
              and w.memberId = :memberId
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            order by w.lastWatchedAt desc
            """,
            countQuery = """
            select count(w) from LessonWatch w, Lesson l, Course c
            where w.lessonId = l.lessonId and l.courseId = c.courseId
              and w.memberId = :memberId
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            """)
    Page<LessonWatch> findVisibleByMemberId(@Param("memberId") Long memberId,
                                            @Param("courseStatus") String courseStatus,
                                            Pageable pageable);
}