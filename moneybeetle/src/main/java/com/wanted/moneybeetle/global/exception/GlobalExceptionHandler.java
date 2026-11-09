package com.wanted.moneybeetle.global.exception;

import com.wanted.moneybeetle.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   업무 예외·없는 주소·업로드 용량 초과·예상하지 못한 예외를 API는 ApiResponse, 화면은 오류 페이지로 바꾼다
*  */
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
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("없는 주소 요청 ({})", request.getRequestURI());
        return makeResponse(CommonErrorCode.NOT_FOUND, request);
    }

    // 업로드 용량 초과 (yaml max-file-size 초과) → 400 FILE_TOO_LARGE
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("업로드 용량 초과 ({})", request.getRequestURI());
        return makeResponse(CommonErrorCode.FILE_TOO_LARGE, request);
    }

    // 예상하지 못한 모든 예외
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("예상하지 못한 오류 ({})", request.getRequestURI(), e);
        return makeResponse(CommonErrorCode.INTERNAL_ERROR, request);
    }

    // 주소를 보고 JSON 상자 or 에러 페이지
    private Object makeResponse(ErrorCode errorCode, HttpServletRequest request) {

        // 1) API 요청이면 JSON 상자 ("/api/" 로 비교해야 /apple 같은 화면 주소를 API로 잘못 보지 않음)
        if (request.getRequestURI().startsWith("/api/")) {
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