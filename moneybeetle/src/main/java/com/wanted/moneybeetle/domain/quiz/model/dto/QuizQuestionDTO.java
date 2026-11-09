package com.wanted.moneybeetle.domain.quiz.model.dto;

import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 39. 퀴즈 문제 (정답 isCorrect는 일부러 넣지 않는다, SP-35)
 *  */
@Getter
public class QuizQuestionDTO {

    private final List<QuestionItem> questions;

    private QuizQuestionDTO(List<QuestionItem> questions) {
        this.questions = questions;
    }

    public static QuizQuestionDTO from(List<QuizQuestion> questions, Map<Long, List<QuizChoice>> choicesByQuestion) {
        return new QuizQuestionDTO(questions.stream()
                .map(question -> QuestionItem.from(question,
                        choicesByQuestion.getOrDefault(question.getQuizQuestionId(), List.of())))
                .toList());
    }

    @Getter
    public static class QuestionItem {
        private final Long questionId;
        private final String content;
        private final List<ChoiceItem> choices;

        private QuestionItem(Long questionId, String content, List<ChoiceItem> choices) {
            this.questionId = questionId;
            this.content = content;
            this.choices = choices;
        }

        static QuestionItem from(QuizQuestion question, List<QuizChoice> choices) {
            return new QuestionItem(question.getQuizQuestionId(), question.getContent(),
                    choices.stream().map(ChoiceItem::from).toList());
        }
    }

    @Getter
    public static class ChoiceItem {
        private final Long choiceId;
        private final String content;

        private ChoiceItem(Long choiceId, String content) {
            this.choiceId = choiceId;
            this.content = content;
        }

        static ChoiceItem from(QuizChoice choice) {
            return new ChoiceItem(choice.getQuizChoiceId(), choice.getContent());
        }
    }
}
