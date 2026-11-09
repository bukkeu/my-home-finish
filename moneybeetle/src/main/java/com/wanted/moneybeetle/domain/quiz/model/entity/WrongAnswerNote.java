package com.wanted.moneybeetle.domain.quiz.model.entity;

import com.wanted.moneybeetle.global.common.CreatedTimeEntity;
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
 *   테이블 wrong_answer_note (틀린 문제만 한 번씩 저장, 사용자가 지울 때까지 유지, SP-36)
 *  */
@Entity
@Table(name = "wrong_answer_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrongAnswerNote extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long wrongAnswerNoteId;

    private Long memberId;
    private Long quizQuestionId;
    private Long lastChoiceId;          // 마지막으로 고른 오답. 안 풀었으면 null

    private WrongAnswerNote(Long memberId, Long quizQuestionId, Long lastChoiceId) {
        this.memberId = memberId;
        this.quizQuestionId = quizQuestionId;
        this.lastChoiceId = lastChoiceId;

    }

    /* 퀴즈 제출(#20)에서 틀린 문제를 처음 저장할 때 */
    public static WrongAnswerNote create(Long memberId, Long quizQuestionId, Long lastChoiceId) {
        return new WrongAnswerNote(memberId, quizQuestionId, lastChoiceId);
    }

    /* 이미 있는 문제를 또 틀렸을 때 마지막 오답만 바꾼다 (#20) */
    public void changeLastChoice(Long choiceId) {
        this.lastChoiceId = choiceId;
    }
}