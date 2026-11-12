package com.wanted.moneybeetle.domain.inquiry.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
 *   담당 : DBA ① (문의)
 *   문의 게시판 에러 (정책 정의서 3-2 학습). 입력 오류(빈 값·글자 수)는 DTO 검증 메시지로 처리
 *  */
@Getter
@RequiredArgsConstructor
public enum InquiryErrorCode implements ErrorCode {

    INQUIRY_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘은 문의를 더 남길 수 없어요"),        // DP-30, 429
    INQUIRY_ALREADY_ANSWERED(HttpStatus.CONFLICT, "답변이 달린 문의는 수정할 수 없어요"),       // API 50
    INQUIRY_HIDDEN(HttpStatus.CONFLICT, "관리자가 숨긴 문의는 수정할 수 없어요"),                // API 50, DP-22 (#245)
    INQUIRY_REPLY_HIDDEN(HttpStatus.CONFLICT, "관리자가 숨긴 답변은 수정할 수 없어요");          // API 52-1, DP-22 (#370)

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}