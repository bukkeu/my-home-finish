package com.wanted.moneybeetle.domain.operation.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   운영 도메인 에러 코드 (각 도메인이 자기 패키지에 ErrorCode 구현 enum 을 둔다)
*  */
@Getter
public enum OperationErrorCode implements ErrorCode {

    /** 숫자였던 설정에 숫자가 아닌 값을 넣으려 했다 (해금 레벨·통과 점수 등을 읽는 쪽이 깨지지 않게 막는다) */
    SETTING_VALUE_INVALID("SETTING_VALUE_INVALID", HttpStatus.BAD_REQUEST, "설정 값 형식이 올바르지 않습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    OperationErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
