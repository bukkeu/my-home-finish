package com.wanted.moneybeetle.domain.quiz.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   테이블 quiz_answer (응시별 문항 답안과 채점 결과, 지우지 않는다 SP-34)
*  */
@Entity
@Table(name = "quiz_answer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quizAnswerId;

    private Long quizAttemptId;
    private Long quizQuestionId;
    private Long selectedChoiceId;  // 안 풀었으면 null
    private boolean isCorrect;

    private QuizAnswer(Long quizAttemptId, Long quizQuestionId, Long selectedChoiceId, boolean isCorrect) {
        this.quizAttemptId = quizAttemptId;
        this.quizQuestionId = quizQuestionId;
        this.selectedChoiceId = selectedChoiceId;
        this.isCorrect = isCorrect;
    }

    public static QuizAnswer create(Long quizAttemptId, Long quizQuestionId, Long selectedChoiceId,
                                    boolean isCorrect) {
        return new QuizAnswer(quizAttemptId, quizQuestionId, selectedChoiceId, isCorrect);
    }
}
