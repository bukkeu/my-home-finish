package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 quiz_question
 *  */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByCourseIdAndQuizTypeOrderBySortOrderAsc(Long courseId, String quizType);
}