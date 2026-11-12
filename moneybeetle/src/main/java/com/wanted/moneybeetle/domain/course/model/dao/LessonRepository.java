package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;

import java.util.List;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 lesson
*  */
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseIdOrderBySortOrderAsc(Long courseId);
    long countByCourseId(Long courseId);
    Optional<Lesson> findByLessonIdAndCourseId(Long lessonId, Long courseId);

    /* 요약글 수정 (API 152, #366): 영상 행을 아직 읽지 않고 소속 강의 번호만 꺼낸다.
     * 영상을 먼저 읽어 두면 강의 잠금을 기다리는 동안 낡을 수 있어서, 강의를 잠근 뒤 findByIdForUpdate 로 읽는다 */
    @Query("select l.courseId from Lesson l where l.lessonId = :lessonId")
    Optional<Long> findCourseIdById(@Param("lessonId") Long lessonId);

    /* 요약글 수정 (API 152, #366): 영상 행을 잠그고 최신 값으로 읽는다 (MySQL 기본 격리 수준에서는 일반 조회가 낡은 스냅샷을 볼 수 있다).
     * 강의 행을 먼저 잠근 뒤에 부른다 (잠금 순서: 강의 → 영상) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lesson l where l.lessonId = :lessonId")
    Optional<Lesson> findByIdForUpdate(@Param("lessonId") Long lessonId);

    /* 강사 센터 요약글 목록 (API 151, #366): 이 강의들 중 요약글이 있는(비어 있지 않은) 영상을 최근 수정 순으로 한 번에 */
    @Query("""
            select l from Lesson l
            where l.courseId in :courseIds and l.summary is not null and trim(l.summary) <> ''
            order by l.updatedAt desc, l.lessonId desc
            """)
    List<Lesson> findWithSummaryByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    /* 영상을 뺄 수 있는지 (API 78): 시청 기록이나 "마지막으로 본 영상"이 가리키면 지울 수 없다 (FK) */
    @Query(value = """
            select (select count(*) from lesson_watch w where w.lesson_id in (:lessonIds))
                 + (select count(*) from learning_progress p where p.last_lesson_id in (:lessonIds))
            """, nativeQuery = true)
    long countUsages(@Param("lessonIds") List<Long> lessonIds);

    /* 영상 파일을 바꿨을 때 (API 78, #31): 예전 영상의 시청 기록이 새 영상에 적용되지 않게
     * 본 시간·완료를 처음으로 돌리고, 이 영상의 이어보기 위치를 0초로 돌린다 (기록 행 자체는 남긴다) */
    @Modifying
    @Query(value = "update lesson_watch set watched_seconds = 0, is_completed = 0 where lesson_id = :lessonId",
            nativeQuery = true)
    int resetWatches(@Param("lessonId") Long lessonId);

    @Modifying
    @Query(value = "update learning_progress set last_position_seconds = 0 where last_lesson_id = :lessonId",
            nativeQuery = true)
    int resetLastPositions(@Param("lessonId") Long lessonId);
}
