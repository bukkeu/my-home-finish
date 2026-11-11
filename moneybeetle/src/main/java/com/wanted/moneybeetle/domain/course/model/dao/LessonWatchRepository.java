package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
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
 *   테이블 lesson_watch (시청 기록)
 *  */
public interface LessonWatchRepository extends JpaRepository<LessonWatch, Long> {

    Optional<LessonWatch> findByMemberIdAndLessonId(Long memberId, Long lessonId);

    /* 강의 상세: 이 회원이 이 영상들 중 끝까지 본 영상 (#52, 목차 완료 표시) */
    List<LessonWatch> findByMemberIdAndLessonIdInAndIsCompletedTrue(Long memberId, Collection<Long> lessonIds);

    /* 강의 목록: 강의별로 끝까지 본 영상 수를 한 번에 (#52, 진행률) */
    @Query("""
            select l.courseId as courseId, count(w) as watched
            from LessonWatch w, Lesson l
            where w.lessonId = l.lessonId
              and w.memberId = :memberId and w.isCompleted = true
              and l.courseId in :courseIds
            group by l.courseId
            """)
    List<CourseWatchedCount> countCompletedByCourse(@Param("memberId") Long memberId,
                                                    @Param("courseIds") Collection<Long> courseIds);

    interface CourseWatchedCount {
        Long getCourseId();
        long getWatched();
    }

    /* 퀴즈 잠금 해제 확인 (API 41·42): 이 영상들 중 끝까지 본 수 */
    long countByMemberIdAndLessonIdInAndIsCompletedTrue(Long memberId, Collection<Long> lessonIds);

    /* 시청 기록: 허가 + 공개 + 삭제 안 된 강의의 영상만, 최근 본 순 (DH-01, SP-31)
     * 페이지 수가 맞도록 DB에서 강의를 조인해 거른다. 기록 자체는 지우지 않는다 */
    @Query(value = """
            select w from LessonWatch w, Lesson l, Course c
            where w.lessonId = l.lessonId and l.courseId = c.courseId
              and w.memberId = :memberId
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            order by w.lastWatchedAt desc, w.lessonWatchId desc
            """,
            countQuery = """
            select count(w) from LessonWatch w, Lesson l, Course c
            where w.lessonId = l.lessonId and l.courseId = c.courseId
              and w.memberId = :memberId
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            """)
    Page<LessonWatch> findVisibleByMemberId(@Param("memberId") Long memberId,
                                            @Param("courseStatus") CourseStatus courseStatus,
                                            Pageable pageable);

    /* 홈·내 정보의 본 영상 수 (#188): 시청 기록(findVisibleByMemberId)과 같은 조건으로 개수만 센다 (영상·강의 정보를 읽지 않음) */
    @Query("""
            select count(w) from LessonWatch w, Lesson l, Course c
            where w.lessonId = l.lessonId and l.courseId = c.courseId
              and w.memberId = :memberId
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            """)
    long countVisibleByMemberId(@Param("memberId") Long memberId, @Param("courseStatus") CourseStatus courseStatus);

    /* 홈 '이어서 볼 강의'의 마지막 영상 (#188): 이 강의에서 최근 본 순으로. 시청 기록(findVisibleByMemberId)과 같은 조건·순서라
     * 첫 줄이 "최근 시청 목록에서 이 강의의 첫 항목"과 같다. 한 건만 필요하면 PageRequest.of(0, 1) 로 부른다 */
    @Query("""
            select w from LessonWatch w, Lesson l, Course c
            where w.lessonId = l.lessonId and l.courseId = c.courseId
              and w.memberId = :memberId and c.courseId = :courseId
              and c.status = :courseStatus and c.isPublic = true and c.deletedAt is null
            order by w.lastWatchedAt desc, w.lessonWatchId desc
            """)
    List<LessonWatch> findLatestVisibleByMemberIdAndCourseId(@Param("memberId") Long memberId,
                                                             @Param("courseId") Long courseId,
                                                             @Param("courseStatus") CourseStatus courseStatus,
                                                             Pageable pageable);
}
