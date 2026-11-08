package com.wanted.moneybeetle.global.security;

import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/* comment.
*   담당 : SCM (회원·공통)
*   레벨 해금 확인 (SP-24, INV-01, SUB-01) - 레벨이 모자라면 LEVEL_LOCKED (403)
*   현재 레벨은 member 테이블의 level 값으로 확인, 기준 레벨은 system_setting 값
*  */
@Component
public class LevelCheckInterceptor implements HandlerInterceptor {    // ② 인터셉터 인터페이스 (강의와 같음)

    // 컨트롤러 도착 "전"에 실행
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {   // ③ 메서드 이름

        // 컨트롤러 메서드가 아니면(정적 파일 등) 검사하지 않고 통과
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // @RequireLevel 찾기 : 메서드에 없으면 클래스에서 찾는다 (강의에 없는 부분)
        RequireLevel requireLevel = handlerMethod.getMethodAnnotation(RequireLevel.class);
        if (requireLevel == null) {
            requireLevel = handlerMethod.getBeanType().getAnnotation(RequireLevel.class);
        }

        // ④ 어노테이션이 없으면 레벨 조건이 없는 API → 그냥 통과 (true / false 중 무엇?)
        if (requireLevel == null) {
            return true;
        }

        // 필요한 레벨 (TODO: system_setting 값을 읽는 Service 가 생기면 그 값으로 바꾼다)
        int requiredLevel = requireLevel.value().getDefaultLevel();

        // 지금 회원의 레벨
        int currentLevel = findCurrentLevel(request);

        // ⑤ 레벨이 모자라면 업무 예외 → GlobalExceptionHandler 가 403 LEVEL_LOCKED 로 응답
        if (currentLevel < requiredLevel) {
            throw new BusinessException(CommonErrorCode.LEVEL_LOCKED);
        }

        return true;
    }

    /* comment.
     *   TODO(#4, 김예린 연동) 로그인 회원의 레벨 조회
     *   AuthDetails.getMemberId() 로 회원 번호를 꺼내 GrowthService.getLevel(memberId) 로 조회한다 (역할분담 §7)
     *   두 메서드가 올라오기 전까지는 0 을 돌려준다 → @RequireLevel 이 붙은 API 는 모두 막힌다 (안전한 쪽으로 실패)
     *  */
    private int findCurrentLevel(HttpServletRequest request) {
        return 0;
    }
}