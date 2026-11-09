package com.wanted.moneybeetle.domain.quiz.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   제출 답안 목록 (questionId, choiceId)
*  */

@Getter
@NoArgsConstructor
public class QuizSubmitDTO {
    @NotNull(message = "답안을 보내 주세요")
    private List<@NotNull(message = "답안 항목은 비울 수 없어요") AnswerItem> answers;

    @Getter
    @NoArgsConstructor
    public static class AnswerItem {
        private Long questionId;
        private Long choiceId;      // 안 푼 문제는 null
    }

}
