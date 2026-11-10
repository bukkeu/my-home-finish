package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /* 영상을 뺄 수 있는지 (API 78): 시청 기록이나 "마지막으로 본 영상"이 가리키면 지울 수 없다 (FK) */
    @Query(value = """
            select (select count(*) from lesson_watch w where w.lesson_id in (:lessonIds))
                 + (select count(*) from learning_progress p where p.last_lesson_id in (:lessonIds))
            """, nativeQuery = true)
    long countUsages(@Param("lessonIds") List<Long> lessonIds);
}
