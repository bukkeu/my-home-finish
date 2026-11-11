package com.wanted.moneybeetle.domain.quiz.model.dto;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   관리자 검수 상세(API 90)의 퀴즈 한 문항. 정답·해설 포함 (관리자 전용)
 *  */
public record QuizReviewDTO(
        Long quizQuestionId,
        String quizType,
        String questionType,
        String content,
        String explanation,
        int sortOrder,
        List<Choice> choices
) {
    public record Choice(Long quizChoiceId, String content, boolean isCorrect, int sortOrder) {
        static Choice from(QuizChoice choice) {
            return new Choice(choice.getQuizChoiceId(), choice.getContent(), choice.isCorrect(), choice.getSortOrder());
        }
    }

    public static QuizReviewDTO of(QuizQuestion question, List<QuizChoice> choices) {
        return new QuizReviewDTO(question.getQuizQuestionId(), question.getQuizType(), question.getQuestionType(),
                question.getContent(), question.getExplanation(), question.getSortOrder(),
                choices.stream().map(Choice::from).toList());
    }
}