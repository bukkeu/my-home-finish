package com.wanted.moneybeetle.domain.quiz.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   강사 퀴즈 일괄 저장 (API 79 테스트 OX 5문제 / API 80 4지선다 5~15문제)
*   - 테스트 퀴즈: { "questions": [ { "content": "...", "answer": "O", "explanation": "..." } ] }
*   - 퀴즈:       { "questions": [ { "content": "...", "choices": ["..","..","..",".."], "answerIndex": 3, "explanation": "..." } ] }
*   - 문항 수(QUIZ_COUNT_INVALID)와 종류별 형식(answer O/X, 보기 4개, answerIndex 0~3)은 서비스가 검사한다
*  */
public record QuizSaveDTO(
        @NotNull(message = "문제를 입력해 주세요") List<@Valid @NotNull Question> questions
) {

    public record Question(
            @NotBlank(message = "문제를 입력해 주세요")
            @Size(max = 500, message = "문제는 500자까지 쓸 수 있어요") String content,
            String answer,              // 테스트 퀴즈: "O" 또는 "X"
            List<String> choices,       // 퀴즈: 보기 4개
            Integer answerIndex,        // 퀴즈: 정답 보기 위치 0~3
            @NotBlank(message = "해설을 입력해 주세요") String explanation
    ) {
    }
}
