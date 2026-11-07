package com.wanted.moneybeetle.global.exception;

import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // 우리가 던진 업무 예외
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 예외 : {} ({})", errorCode.getCode(), request.getRequestURI());
        return makeResponse(errorCode, request);
    }

    // 없는 주소 요청 → 404
    @ExceptionHandler(NoResourceFoundException.class)                       // ① 로그에 찍힌 예외 이름
    public Object handleNoResource(NoResourceFoundException e, HttpServletRequest request) {   // ① 같은 이름
        log.warn("없는 주소 요청 ({})", request.getRequestURI());
        return makeResponse(CommonErrorCode.NOT_FOUND, request);              // ② 어떤 에러 코드?
    }

    // 예상하지 못한 모든 예외
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("예상하지 못한 오류 ({})", request.getRequestURI(), e);
        return makeResponse(CommonErrorCode.INTERNAL_ERROR, request);
    }

    // 주소를 보고 JSON 상자 or 에러 페이지
    private Object makeResponse(ErrorCode errorCode, HttpServletRequest request) {

        // 1) API 요청이면 JSON 상자
        if (request.getRequestURI().startsWith("/api")) {
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ApiResponse.fail(errorCode));
        }

        // 2) 화면 요청이면 에러 페이지
        ModelAndView mv = new ModelAndView(errorPage(errorCode.getStatus()));
        mv.setStatus(errorCode.getStatus());
        mv.addObject("message", errorCode.getMessage());
        return mv;
    }

    // 상태 번호 → 페이지 이름 (403, 404, 500 세 개만 존재)
    private String errorPage(HttpStatus status) {
        if (status == HttpStatus.FORBIDDEN) {
            return "error/403";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "error/404";
        }
        return "error/500";
    }
}