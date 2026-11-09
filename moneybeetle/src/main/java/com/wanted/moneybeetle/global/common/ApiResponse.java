package com.wanted.moneybeetle.global.common;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   공통 응답 형식 { success, data, error } (API 명세서 1-2)
*   - success(data) / fail(errorCode) 정적 팩토리 메서드
*  */

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorDetail error;

    // 생성자
    private ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    // 성공 시
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    // 실패 시
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        ErrorDetail detail = new ErrorDetail(errorCode.getCode(), errorCode.getMessage());
        return new ApiResponse<>(false, null, detail);
    }

    // 에러 내용
    @Getter
    public static class ErrorDetail {
        private final String code;
        private final String message;

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

}
