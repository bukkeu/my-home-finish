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
    REWARD_RULE_NOT_FOUND("REWARD_RULE_NOT_FOUND", HttpStatus.NOT_FOUND, "사용 중인 보상 규칙이 없습니다."),
    /** 보상 규칙에 지급할 캐시·경험치가 있는데, 그 규칙 코드가 어떤 원인 종류로 기록할지 정해져 있지 않다 (설정 오류) */
    REWARD_NOT_SUPPORTED("REWARD_NOT_SUPPORTED", HttpStatus.INTERNAL_SERVER_ERROR, "지원하지 않는 보상 설정입니다."),
    /** 같은 원인(시즌 참가, 청약통장 납입 등)으로 이미 캐시를 썼다 (이중 차감 방지) */
    ALREADY_USED("ALREADY_USED", HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    INVALID_HISTORY_TYPE("INVALID_HISTORY_TYPE", HttpStatus.BAD_REQUEST, "내역 종류는 EARN 또는 USE 만 쓸 수 있습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    GrowthErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
