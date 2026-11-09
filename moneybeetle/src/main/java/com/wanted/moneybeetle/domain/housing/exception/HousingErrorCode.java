package com.wanted.moneybeetle.domain.housing.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
 *   담당 : SCM (모의 청약)
 *   NOT_IN_APPLY_PERIOD, ALREADY_APPLIED, ALREADY_PAID_TODAY, NOT_ANNOUNCED_YET, BEFORE_ANNOUNCE_DATE, COMPLEX_HAS_APPLICANTS
 *  */
@Getter
@RequiredArgsConstructor
public enum HousingErrorCode implements ErrorCode {          // ① #2 에서 만든 에러 코드 규칙(인터페이스)

    // ② 위 표의 3개를 CommonErrorCode 처럼 : 이름(HttpStatus.상태, "메시지"),
    ALREADY_PAID_TODAY(HttpStatus.CONFLICT, "오늘은 이미 납입했어요"),
    DEPOSIT_BELOW_MIN(HttpStatus.BAD_REQUEST, "최소 20,000 캐시부터 납입할 수 있어요"),
    DEPOSIT_ABOVE_MAX(HttpStatus.BAD_REQUEST, "한 번에 최대 500,000 캐시까지 납입할 수 있어요");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();                                      // ③ CommonErrorCode 와 같이 상수 이름 그대로
    }
}