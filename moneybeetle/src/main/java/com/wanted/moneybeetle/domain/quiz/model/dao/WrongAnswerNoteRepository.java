package com.wanted.moneybeetle.domain.quiz.model.dao;

import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 wrong_answer_note
 *  */
public interface WrongAnswerNoteRepository extends JpaRepository<WrongAnswerNote, Long> {

    /* 오답노트 목록: 보이는 강의(courseIds)의 문항만, 최근 순. 같은 시각이면 ID로 순서 고정
     * 페이지 수가 맞도록 DB에서 문항을 조인해 거른다 */
    @Query(value = """
            select w from WrongAnswerNote w, QuizQuestion q
            where w.quizQuestionId = q.quizQuestionId
              and w.memberId = :memberId and q.courseId in :courseIds
            order by w.createdAt desc, w.wrongAnswerNoteId desc
            """,
            countQuery = """
            select count(w) from WrongAnswerNote w, QuizQuestion q
            where w.quizQuestionId = q.quizQuestionId
              and w.memberId = :memberId and q.courseId in :courseIds
            """)
    Page<WrongAnswerNote> findByMemberIdAndCourseIds(@Param("memberId") Long memberId,
                                                     @Param("courseIds") List<Long> courseIds,
                                                     Pageable pageable);

    /* 다시 풀기: 요청한 문항 중 내 오답노트에 있는 것만 */
    List<WrongAnswerNote> findByMemberIdAndQuizQuestionIdIn(Long memberId, List<Long> quizQuestionIds);

    /* 삭제: 내 오답만 지운다 (남의 ID면 아무것도 안 지워짐) */
    long deleteByWrongAnswerNoteIdAndMemberId(Long wrongAnswerNoteId, Long memberId);
}