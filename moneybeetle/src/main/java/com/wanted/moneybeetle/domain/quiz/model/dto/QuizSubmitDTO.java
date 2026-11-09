package com.wanted.moneybeetle.domain.quiz.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /* 한 번에 보낼 수 있는 답안 수 (퀴즈 최대 15문제, 오답 다시 풀기는 페이지 최대 크기 100에 맞춤) */
    public static final int MAX_ANSWERS = 100;

    @NotNull(message = "답안을 보내 주세요")
    @Size(max = MAX_ANSWERS, message = "답안은 한 번에 100개까지 보낼 수 있어요")
    private List<@NotNull(message = "답안 항목은 비울 수 없어요") AnswerItem> answers;

    @Getter
    @NoArgsConstructor
    public static class AnswerItem {
        private Long questionId;
        private Long choiceId;      // 안 푼 문제는 null
    }

}
