package com.wanted.moneybeetle.domain.quiz.model.dto;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 41 퀴즈 문제 { passScore, questions } (정답·해설은 넣지 않는다, SP-35)
 *  */
public record QuizPaperDTO(int passScore, List<QuizQuestionDTO.QuestionItem> questions) {
}
