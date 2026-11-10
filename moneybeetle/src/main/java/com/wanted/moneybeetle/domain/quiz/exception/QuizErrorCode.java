package com.wanted.moneybeetle.domain.quiz.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   QUIZ_COUNT_INVALID (#32), QUIZ_LOCKED (#20)
*  */
@Getter
@RequiredArgsConstructor
public enum QuizErrorCode implements ErrorCode {

    QUIZ_COUNT_INVALID(HttpStatus.BAD_REQUEST, "테스트 퀴즈는 5문제, 퀴즈는 5~15문제가 필요해요"),   // API 79·80·81 (DP-34)
    QUIZ_LOCKED(HttpStatus.FORBIDDEN, "영상을 모두 본 뒤에 퀴즈를 풀 수 있어요");                    // API 41·42 (QST-02)

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
