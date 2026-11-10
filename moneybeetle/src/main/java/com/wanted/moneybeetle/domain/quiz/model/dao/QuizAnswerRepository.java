package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 quiz_answer
*  */
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    /* 퀴즈 결과 다시 보기 (API 43) */
    List<QuizAnswer> findByQuizAttemptIdOrderByQuizAnswerIdAsc(Long quizAttemptId);
}
