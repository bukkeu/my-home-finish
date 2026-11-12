package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 quiz_question
 *  */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByCourseIdAndQuizTypeOrderBySortOrderAsc(Long courseId, String quizType);
    /* 관리자 검수(API 90): 이 강의의 모든 문항. 종류 → 순서 → ID로 정렬 고정 */
    // 보관한 문항(TEST_OLD / MAIN_OLD, #31)은 빼고 지금 쓰는 TEST·MAIN만
    @Query("""
            select q from QuizQuestion q
            where q.courseId = :courseId and q.quizType in ('TEST', 'MAIN')
            order by q.quizType asc, q.sortOrder asc, q.quizQuestionId asc
            """)
    List<QuizQuestion> findByCourseIdOrderByQuizTypeAscSortOrderAscQuizQuestionIdAsc(@Param("courseId") Long courseId);

    /* 강사 센터 퀴즈 목록 (API 150, #366): 강의마다 지금 쓰는 테스트 퀴즈·퀴즈 문항 수와 가장 늦게 바뀐 시각을 한 번에.
     * 보관한 문항(TEST_OLD / MAIN_OLD)은 세지 않는다. 문항이 없는 강의는 행이 나오지 않는다 */
    @Query("""
            select new com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO(
                q.courseId,
                count(case when q.quizType = 'TEST' then 1 end),
                count(case when q.quizType = 'MAIN' then 1 end),
                max(q.updatedAt))
            from QuizQuestion q
            where q.courseId in :courseIds and q.quizType in ('TEST', 'MAIN')
            group by q.courseId
            """)
    List<CourseQuizStatDTO> findQuizStatsByCourseIds(@Param("courseIds") java.util.Collection<Long> courseIds);

    /* 보기·정답만 바뀐 문항의 수정 시각을 갱신한다 (API 79·80, #384).
     * 보기는 quiz_choice 라서 문항 행은 바뀌지 않아 감사 기능(@LastModifiedDate)이 updated_at 을 채우지 않는다.
     * 먼저 밀린 변경을 반영하고(flushAutomatically) 이 문항의 updated_at 만 직접 바꾼다 */
    @Modifying(flushAutomatically = true)
    @Query("update QuizQuestion q set q.updatedAt = :now where q.quizQuestionId = :quizQuestionId")
    int touchUpdatedAt(@Param("quizQuestionId") Long quizQuestionId, @Param("now") LocalDateTime now);

    /* 검수 요청 조건 (API 81): 종류별 문항 수 */
    long countByCourseIdAndQuizType(Long courseId, String quizType);

    /* 문항을 뺄 수 있는지 (API 79·80): 응시 답안이나 오답노트가 가리키면 지울 수 없다 (FK) */
    @Query(value = """
            select (select count(*) from quiz_answer a where a.quiz_question_id in (:questionIds))
                 + (select count(*) from wrong_answer_note w where w.quiz_question_id in (:questionIds))
            """, nativeQuery = true)
    long countUsages(@Param("questionIds") List<Long> questionIds);
}