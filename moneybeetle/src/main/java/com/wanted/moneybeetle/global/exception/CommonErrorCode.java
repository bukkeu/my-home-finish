package com.wanted.moneybeetle.global.exception;

/* comment.
*   담당 : SCM (회원·공통)
*   공통 에러 : UNAUTHORIZED, SESSION_EXPIRED, FORBIDDEN, LEVEL_LOCKED, NOT_FOUND,
*   DUPLICATE_REQUEST, IDEMPOTENCY_KEY_CONFLICT, FORBIDDEN_WORD, INTERNAL_ERROR (API 명세서 4장)
*   : 양식에 맞춰 미리 써 둔 신고서 묶음
*  */

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode{

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),                                                   // 401
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인 시간이 지났어요. 다시 로그인 해주세요"),                         // 401
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 페이지를 볼 수 있는 권한이 없어요"),                                        //403
    LEVEL_LOCKED(HttpStatus.FORBIDDEN, "아직 열리지 않은 기능이에요"),                                             //403
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없는 페이지예요"),                                                   // 404
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리된 요청이에요       "),                                      // 409
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 요청 번호로 내용이 다른 요청을 보냈어요. 새로 시도해 주세요"),   // 409
    FORBIDDEN_WORD(HttpStatus.BAD_REQUEST, "사용할 수 없는 표현이 들어 있어요"),                                    // 400
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리하지 못했어요. 내정보는 바뀌지 않았어요");                  // 500


    private final HttpStatus status;
    private final String message;


    @Override
    public String getCode() {
        return name();
    }

}
