package com.wanted.moneybeetle.domain.quiz.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /* 답안을 "문항 ID → 고른 보기 ID"로 바꾼다 (채점용, #294에서 QuizService 에서 옮김).
     * 같은 문항이 여러 번 오면 첫 답만 쓴다. 첫 답이 미응답(choiceId null)이어도 그대로 유지한다. 문항 ID가 없는 답은 버린다 */
    public Map<Long, Long> toSelectedMap() {
        Map<Long, Long> selected = new HashMap<>();
        for (AnswerItem answer : answers) {
            if (answer.getQuestionId() != null && !selected.containsKey(answer.getQuestionId())) {
                selected.put(answer.getQuestionId(), answer.getChoiceId());
            }
        }
        return selected;
    }

    @Getter
    @NoArgsConstructor
    public static class AnswerItem {
        private Long questionId;
        private Long choiceId;      // 안 푼 문제는 null
    }

}
