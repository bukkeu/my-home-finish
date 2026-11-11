package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 quiz_attempt
*  */
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
}
