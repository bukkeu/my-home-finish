package com.wanted.moneybeetle.domain.growth.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ② (성장)
*   성장 도메인 에러 코드 (각 도메인이 자기 패키지에 ErrorCode 구현 enum 을 둔다)
*   #2 에서 global.exception.ErrorCode 가 getCode / getStatus(HttpStatus) / getMessage 로 정해져
*   상태 필드를 int httpStatus -> HttpStatus status 로 맞췄다 (Lombok @Getter 가 getStatus() 를 만든다)
*  */
@Getter
public enum GrowthErrorCode implements ErrorCode {

    NOT_ENOUGH_CASH("NOT_ENOUGH_CASH", HttpStatus.BAD_REQUEST, "캐시가 부족합니다."),
    REASON_REQUIRED("REASON_REQUIRED", HttpStatus.BAD_REQUEST, "사유를 입력해 주세요."),
    INVALID_AMOUNT("INVALID_AMOUNT", HttpStatus.BAD_REQUEST, "금액은 0보다 커야 합니다."),
    REWARD_RULE_NOT_FOUND("REWARD_RULE_NOT_FOUND", HttpStatus.NOT_FOUND, "사용 중인 보상 규칙이 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    GrowthErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
