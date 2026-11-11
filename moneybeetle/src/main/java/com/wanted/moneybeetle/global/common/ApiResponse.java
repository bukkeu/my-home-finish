package com.wanted.moneybeetle.global.common;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;

import java.util.List;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   공통 응답 형식 { success, data, error } (API 명세서 1-2)
*   - success(data) / fail(errorCode) / fail(errorCode, fields) 정적 팩토리 메서드
*   - error = { code, message, fields }. fields 는 입력값 오류(@Valid)일 때만 채우고 나머지는 null
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

    // 실패 시 (입력칸별 오류 없음 → fields: null)
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, null);
    }

    // 입력값 오류 시 (#142) : 입력칸별 안내를 fields 에 담는다 (API 명세서 1-2, EP-01)
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, List<FieldMessage> fields) {
        return fail(errorCode, errorCode.getMessage(), fields);
    }

    // 문구만 따로 줄 때 (#327) : 운영 설정 값을 넣은 업무 예외 문구. 코드는 errorCode 그대로
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, List<FieldMessage> fields) {
        ErrorDetail detail = new ErrorDetail(errorCode.getCode(), message, fields);
        return new ApiResponse<>(false, null, detail);
    }

    // 에러 내용 { code, message, fields }
    @Getter
    public static class ErrorDetail {
        private final String code;
        private final String message;
        private final List<FieldMessage> fields;      // 입력값 오류가 아니면 null

        public ErrorDetail(String code, String message, List<FieldMessage> fields) {
            this.code = code;
            this.message = message;
            this.fields = fields;
        }
    }

    // 입력칸 하나의 오류 { "field": "nickname", "message": "..." }. 특정 칸이 아닌 오류(여러 칸 비교 등)는 field 가 null
    @Getter
    public static class FieldMessage {
        private final String field;
        private final String message;

        public FieldMessage(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }

}
