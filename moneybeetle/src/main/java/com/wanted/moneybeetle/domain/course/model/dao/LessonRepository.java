package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 lesson
*  */
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseIdOrderBySortOrderAsc(Long courseId);
    long countByCourseId(Long courseId);
}
