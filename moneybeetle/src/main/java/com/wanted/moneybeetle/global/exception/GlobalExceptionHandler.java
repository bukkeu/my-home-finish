package com.wanted.moneybeetle.global.exception;

import com.wanted.moneybeetle.global.common.ApiResponse;
import com.wanted.moneybeetle.global.common.ApiResponse.FieldMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   업무 예외·없는 주소·업로드 용량 초과·입력값 오류·예상하지 못한 예외를 API는 ApiResponse, 화면은 오류 페이지로 바꾼다
*   #142
*   - @Valid 실패 → 400 INVALID_INPUT + error.fields (입력칸별 안내, API 명세서 1-2)
*   - 본문 JSON 형식 오류·필수 파라미터 누락·파라미터 타입 오류 → 400 INVALID_INPUT
*   - 인증·권한 예외(AuthenticationException, AccessDeniedException)는 여기서 잡지 않고 다시 던진다
*     → Spring Security 의 SecurityErrorHandler 가 401·403 으로 답한다
*  */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // 우리가 던진 업무 예외
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 예외 : {} ({})", errorCode.getCode(), request.getRequestURI());
        return makeResponse(errorCode, null, request);
    }

    // @Valid 검증 실패 → 400 + 입력칸별 안내
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleInvalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldMessage> fields = new ArrayList<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.add(new FieldMessage(error.getField(), error.getDefaultMessage())));
        // 특정 칸이 아닌 오류 (예: 클래스에 붙인 "비밀번호 확인 일치" 검사) → field 없이 문구만
        e.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.add(new FieldMessage(null, error.getDefaultMessage())));
        log.warn("입력값 오류 {}건 ({})", fields.size(), request.getRequestURI());
        return makeResponse(CommonErrorCode.INVALID_INPUT, fields, request);
    }

    // 본문 JSON 형식 오류, 필수 파라미터 누락, 파라미터 타입 오류(?month=abc, /complexes/abc) → 400
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public Object handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("잘못된 요청 : {} ({})", e.getClass().getSimpleName(), request.getRequestURI());
        return makeResponse(CommonErrorCode.INVALID_INPUT, null, request);
    }

    // 없는 주소 요청 → 404
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("없는 주소 요청 ({})", request.getRequestURI());
        return makeResponse(CommonErrorCode.NOT_FOUND, null, request);
    }

    // 업로드 용량 초과 (yaml max-file-size 초과) → 400 FILE_TOO_LARGE
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("업로드 용량 초과 ({})", request.getRequestURI());
        return makeResponse(CommonErrorCode.FILE_TOO_LARGE, null, request);
    }

    // 인증·권한 예외는 Spring Security 몫 : 그대로 다시 던지면 필터(ExceptionTranslationFilter)가 받아
    // SecurityErrorHandler 가 401·403 으로 답한다. 이 핸들러가 없으면 아래 Exception 핸들러가 500 으로 잡는다
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void passToSecurity(RuntimeException e) {   // 두 예외 모두 RuntimeException 이라 throws 가 필요 없다
        throw e;
    }

    // 예상하지 못한 모든 예외
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("예상하지 못한 오류 ({})", request.getRequestURI(), e);
        return makeResponse(CommonErrorCode.INTERNAL_ERROR, null, request);
    }

    // 주소를 보고 JSON 상자 or 에러 페이지
    private Object makeResponse(ErrorCode errorCode, List<FieldMessage> fields, HttpServletRequest request) {

        // 1) API 요청이면 JSON 상자 ("/api/" 로 비교해야 /apple 같은 화면 주소를 API로 잘못 보지 않음)
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ApiResponse.fail(errorCode, fields));
        }

        // 2) 화면 요청이면 에러 페이지
        ModelAndView mv = new ModelAndView(errorPage(errorCode.getStatus()));
        mv.setStatus(errorCode.getStatus());
        mv.addObject("message", errorCode.getMessage());
        return mv;
    }

    // 상태 번호 → 페이지 이름 (400, 403, 404, 500 네 개만 존재, EP-04)
    private String errorPage(HttpStatus status) {
        if (status == HttpStatus.BAD_REQUEST) {
            return "error/400";              // #114 : 업로드 용량 초과(FILE_TOO_LARGE)·입력 오류가 500 화면으로 보이지 않게
        }
        if (status == HttpStatus.FORBIDDEN) {
            return "error/403";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "error/404";
        }
        return "error/500";
    }
}
