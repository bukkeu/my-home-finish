package com.wanted.moneybeetle.domain.quiz.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   QUIZ_COUNT_INVALID (#32), QUIZ_LOCKED (#20), QUIZ_FILE_INVALID (#368, 서수영)
*  */
@Getter
@RequiredArgsConstructor
public enum QuizErrorCode implements ErrorCode {

    QUIZ_COUNT_INVALID(HttpStatus.BAD_REQUEST, "테스트 퀴즈는 5문제, 퀴즈는 5~15문제가 필요해요"),   // API 79·80·81 (DP-34)
    QUIZ_LOCKED(HttpStatus.FORBIDDEN, "영상을 모두 본 뒤에 퀴즈를 풀 수 있어요"),                    // API 41·42 (QST-02)
    // API 153 : 응답 문구는 대부분 "3번째 줄: 정답은 O 또는 X 로 써 주세요"처럼 줄 번호와 이유를 담은 문구로 바뀐다 (BusinessException 의 message)
    QUIZ_FILE_INVALID(HttpStatus.BAD_REQUEST, "퀴즈 문제 파일을 다시 확인해 주세요");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
