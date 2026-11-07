package com.wanted.moneybeetle.domain.growth.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (성장)
*   성장 도메인 에러 코드 (각 도메인이 자기 패키지에 ErrorCode 구현 enum 을 둔다)
*   global.exception.ErrorCode 가 이슈 #2 에서 getCode / getHttpStatus / getMessage 를 요구하면
*   아래 Lombok @Getter 가 만드는 메서드와 이름을 맞춰 둔 상태다. 다르게 정해지면 #2 병합 때 이 파일을 맞춘다.
*  */
@Getter
public enum GrowthErrorCode implements ErrorCode {

    NOT_ENOUGH_CASH("NOT_ENOUGH_CASH", 400, "캐시가 부족합니다."),
    REASON_REQUIRED("REASON_REQUIRED", 400, "사유를 입력해 주세요."),
    INVALID_AMOUNT("INVALID_AMOUNT", 400, "금액은 0보다 커야 합니다."),
    REWARD_RULE_NOT_FOUND("REWARD_RULE_NOT_FOUND", 404, "사용 중인 보상 규칙이 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;

    GrowthErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
