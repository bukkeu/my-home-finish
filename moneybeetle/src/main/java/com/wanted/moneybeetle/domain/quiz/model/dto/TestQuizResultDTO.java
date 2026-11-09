package com.wanted.moneybeetle.domain.quiz.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import lombok.Getter;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 40. 테스트 퀴즈 채점 결과 (정답·해설) -o/x 문제
 *  */
@Getter
public class TestQuizResultDTO {

    private final int correctCount;
    private final List<ResultItem> results;
    private final boolean lessonsUnlocked;   // 다 풀면 영상이 열림

    private TestQuizResultDTO(int correctCount, List<ResultItem> results, boolean lessonsUnlocked) {
        this.correctCount = correctCount;
        this.results = results;
        this.lessonsUnlocked = lessonsUnlocked;
    }

    public static TestQuizResultDTO of(int correctCount, List<ResultItem> results, boolean lessonsUnlocked) {
        return new TestQuizResultDTO(correctCount, results, lessonsUnlocked);
    }

    @Getter
    public static class ResultItem {
        private final Long questionId;

        @JsonProperty("isCorrect")
        private final boolean correct;

        private final Long correctChoiceId;
        private final String explanation;

        private ResultItem(Long questionId, boolean correct, Long correctChoiceId, String explanation) {
            this.questionId = questionId;
            this.correct = correct;
            this.correctChoiceId = correctChoiceId;
            this.explanation = explanation;
        }

        public static ResultItem of(QuizQuestion question, boolean correct, Long correctChoiceId) {
            return new ResultItem(question.getQuizQuestionId(), correct, correctChoiceId, question.getExplanation());
        }
    }
}