package com.wanted.moneybeetle.domain.housing.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
 *   담당 : SCM (모의 청약)
 *   청약 도메인 에러 코드 (API 명세서 4장)
 *   #28 : ALREADY_PAID_TODAY, DEPOSIT_BELOW_MIN, DEPOSIT_ABOVE_MAX
 *   #29 : INVALID_SCORE_INPUT, NOT_IN_APPLY_PERIOD, ALREADY_APPLIED, NOT_ANNOUNCED_YET
 *   #47·#48 에서 추가 : BEFORE_ANNOUNCE_DATE, COMPLEX_HAS_APPLICANTS
 *  */
@Getter
@RequiredArgsConstructor
public enum HousingErrorCode implements ErrorCode {          // ① #2 에서 만든 에러 코드 규칙(인터페이스)

    // #28 청약통장
    ALREADY_PAID_TODAY(HttpStatus.CONFLICT, "오늘은 이미 납입했어요"),
    DEPOSIT_BELOW_MIN(HttpStatus.BAD_REQUEST, "최소 20,000 캐시부터 납입할 수 있어요"),
    DEPOSIT_ABOVE_MAX(HttpStatus.BAD_REQUEST, "한 번에 최대 500,000 캐시까지 납입할 수 있어요"),

    // #29 가점·신청·결과 (정책 정의서 4장. 날짜가 들어가는 문구는 화면이 단지 일정으로 채운다)
    INVALID_SCORE_INPUT(HttpStatus.BAD_REQUEST, "무주택 기간과 부양가족 수는 0 이상으로 입력해 주세요"),
    NOT_IN_APPLY_PERIOD(HttpStatus.CONFLICT, "지금은 신청 기간이 아니에요"),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "신청 완료"),
    NOT_ANNOUNCED_YET(HttpStatus.FORBIDDEN, "아직 발표 전이에요");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();                                      // ③ CommonErrorCode 와 같이 상수 이름 그대로
    }
}