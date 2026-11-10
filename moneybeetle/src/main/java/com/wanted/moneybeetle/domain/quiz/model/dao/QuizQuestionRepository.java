package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 quiz_question
 *  */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByCourseIdAndQuizTypeOrderBySortOrderAsc(Long courseId, String quizType);
    /* 관리자 검수(API 90): 이 강의의 모든 문항. 종류 → 순서 → ID로 정렬 고정 */
    List<QuizQuestion> findByCourseIdOrderByQuizTypeAscSortOrderAscQuizQuestionIdAsc(Long courseId);

    /* 검수 요청 조건 (API 81): 종류별 문항 수 */
    long countByCourseIdAndQuizType(Long courseId, String quizType);

    /* 문항을 뺄 수 있는지 (API 79·80): 응시 답안이나 오답노트가 가리키면 지울 수 없다 (FK) */
    @Query(value = """
            select (select count(*) from quiz_answer a where a.quiz_question_id in (:questionIds))
                 + (select count(*) from wrong_answer_note w where w.quiz_question_id in (:questionIds))
            """, nativeQuery = true)
    long countUsages(@Param("questionIds") List<Long> questionIds);
}