package com.wanted.moneybeetle.domain.quiz.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   QUIZ_COUNT_INVALID (#32), QUIZ_IN_USE (#31). QUIZ_LOCKED는 퀴즈 제출(#20)에서 추가
*  */
@Getter
@RequiredArgsConstructor
public enum QuizErrorCode implements ErrorCode {

    QUIZ_COUNT_INVALID(HttpStatus.BAD_REQUEST, "테스트 퀴즈는 5문제, 퀴즈는 5~15문제가 필요해요"),   // API 79·80·81 (DP-34)
    QUIZ_IN_USE(HttpStatus.CONFLICT, "푼 기록이 있는 문제가 있어서 문제 수를 줄일 수 없어요");         // API 79·80

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
