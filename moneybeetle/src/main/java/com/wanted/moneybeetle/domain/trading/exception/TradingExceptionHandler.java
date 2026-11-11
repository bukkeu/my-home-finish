package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM (모의투자)
*   CSV 형식 오류(API 130)만 따로 처리 : 공통 응답 모양 { success, data, error } 에 오류 행 목록을 data.errors 로 담는다
*   - 관리자 시장 관리 Controller 에만 적용하고, 나머지 예외는 공통 GlobalExceptionHandler 가 처리한다
*  */

import com.wanted.moneybeetle.domain.trading.controller.AdminMarketController;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminMarketController.class)
public class TradingExceptionHandler {

    @ExceptionHandler(CsvInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleCsvInvalid(CsvInvalidException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", Map.of("errors", e.getErrors()));
        body.put("error", Map.of(
                "code", e.getErrorCode().getCode(),
                "message", e.getErrorCode().getMessage()));
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(body);
    }
}
