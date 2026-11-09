package com.wanted.moneybeetle.domain.quiz.model.dto;

import lombok.Getter;

import java.util.List;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   API 45. 오답 다시 풀기 채점 결과 (문항별 결과는 테스트 퀴즈와 같은 모양)
 *  */
@Getter
public class WrongNoteRetryResultDTO {

    private final List<TestQuizResultDTO.ResultItem> results;

    private WrongNoteRetryResultDTO(List<TestQuizResultDTO.ResultItem> results) {
        this.results = results;
    }

    public static WrongNoteRetryResultDTO of(List<TestQuizResultDTO.ResultItem> results) {
        return new WrongNoteRetryResultDTO(results);
    }
}