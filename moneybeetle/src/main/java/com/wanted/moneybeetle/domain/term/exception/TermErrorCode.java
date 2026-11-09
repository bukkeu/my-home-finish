package com.wanted.moneybeetle.domain.term.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/* comment.
*   담당 : DBA ② (경제 용어)
*   경제 용어 도메인 에러 코드 (API 명세서 96·100)
*  */
@Getter
public enum TermErrorCode implements ErrorCode {

    TERM_DUPLICATED("TERM_DUPLICATED", HttpStatus.CONFLICT, "이미 등록된 용어예요."),
    TERM_NOT_FOUND("TERM_NOT_FOUND", HttpStatus.NOT_FOUND, "사용 중인 경제 용어를 찾을 수 없어요."),
    /** 지난 날짜의 오늘의 용어는 바꿀 수 없다 (SP-47) */
    PAST_DATE_LOCKED("PAST_DATE_LOCKED", HttpStatus.BAD_REQUEST, "지난 날짜는 바꿀 수 없어요."),
    /** 하루 5개를 채우지 못했다 (SP-45, SP-47) */
    TERMS_NOT_ENOUGH("TERMS_NOT_ENOUGH", HttpStatus.BAD_REQUEST, "오늘의 용어는 5개를 지정해야 해요."),
    /** 같은 용어를 두 번 넣었거나 5개보다 많다 */
    TERM_SELECTION_INVALID("TERM_SELECTION_INVALID", HttpStatus.BAD_REQUEST, "서로 다른 용어 5개를 지정해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    TermErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
