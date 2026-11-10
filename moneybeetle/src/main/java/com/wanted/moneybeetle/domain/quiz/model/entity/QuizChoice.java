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
 *   테이블 quiz_choice (보기와 정답 표시. 정답은 제출 전 화면으로 보내지 않음, SP-35)
 *  */
@Entity
@Table(name = "quiz_choice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quizChoiceId;

    private Long quizQuestionId;
    private String content;
    private boolean isCorrect;
    private int sortOrder;

    /* 강사가 새 보기를 만든다 (API 79·80) */
    public static QuizChoice create(Long quizQuestionId, String content, boolean isCorrect, int sortOrder) {
        QuizChoice choice = new QuizChoice();
        choice.quizQuestionId = quizQuestionId;
        choice.sortOrder = sortOrder;
        choice.change(content, isCorrect);
        return choice;
    }

    /* 보기 내용·정답 바꾸기 (API 79·80). 답안·오답노트가 보기 ID를 가리키므로 지우지 않고 고친다 */
    public void change(String content, boolean isCorrect) {
        this.content = content;
        this.isCorrect = isCorrect;
    }
}
