package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 quiz_choice
 *  */

public interface QuizChoiceRepository extends JpaRepository<QuizChoice, Long> {

    /* 여러 문항의 보기를 한 번에 조회 (문항마다 조회하지 않음) */
    List<QuizChoice> findByQuizQuestionIdInOrderBySortOrderAsc(List<Long> quizQuestionIds);
}