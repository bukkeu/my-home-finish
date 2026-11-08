package com.wanted.moneybeetle.domain.course.model.dao;

import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 lesson_watch
*  */
public interface LessonWatchRepository extends JpaRepository<LessonWatch, Long> {

    Optional<LessonWatch> findByMemberIdAndLessonId(Long memberId, Long lessonId);

    Page<LessonWatch> findByMemberIdOrderByLastWatchedAtDesc(Long memberId, Pageable pageable);
}
