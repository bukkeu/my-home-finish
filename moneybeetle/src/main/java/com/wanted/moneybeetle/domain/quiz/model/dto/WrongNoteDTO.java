package com.wanted.moneybeetle.domain.quiz.model.dto;

import com.wanted.moneybeetle.domain.course.model.dto.CourseLabelDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 44. 오답노트 항목
 *  */
@Getter
public class WrongNoteDTO {

    private final Long wrongNoteId;
    private final Long courseId;
    private final String courseTitle;
    private final String categoryName;
    private final Long questionId;
    private final String content;
    private final String myChoice;        // 내가 고른 보기 내용. 안 풀었으면 null
    private final String correctChoice;   // 정답 보기 내용
    private final String explanation;
    private final List<QuizQuestionDTO.ChoiceItem> choices;   // 다시 풀기용 보기 (보기 순서대로, 정답 표시 없음, #71)
    private final LocalDateTime createdAt;

    private WrongNoteDTO(Long wrongNoteId, Long courseId, String courseTitle, String categoryName, Long questionId,
                         String content, String myChoice, String correctChoice, String explanation,
                         List<QuizQuestionDTO.ChoiceItem> choices, LocalDateTime createdAt) {
        this.wrongNoteId = wrongNoteId;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.categoryName = categoryName;
        this.questionId = questionId;
        this.content = content;
        this.myChoice = myChoice;
        this.correctChoice = correctChoice;
        this.explanation = explanation;
        this.choices = choices;
        this.createdAt = createdAt;
    }

    public static WrongNoteDTO of(WrongAnswerNote note, QuizQuestion question, CourseLabelDTO course,
                                  List<QuizChoice> choices, String myChoice, String correctChoice) {
        return new WrongNoteDTO(note.getWrongAnswerNoteId(), course.getCourseId(), course.getTitle(),
                course.getCategoryName(), question.getQuizQuestionId(), question.getContent(),
                myChoice, correctChoice, question.getExplanation(),
                choices.stream().map(QuizQuestionDTO.ChoiceItem::from).toList(), note.getCreatedAt());
    }
}