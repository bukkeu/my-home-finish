package com.wanted.moneybeetle.global.security;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   레벨 해금 확인 (SP-24, INV-01, SUB-01) - 레벨이 모자라면 LEVEL_LOCKED (403)
*   - 현재 레벨 : 로그인 회원 번호로 GrowthService.getLevel(memberId) 를 불러 매번 DB 에서 확인한다 (역할분담 §7)
*   - 기준 레벨 : @RequireLevel 의 UnlockFeature 기본값 (TODO system_setting 값을 읽는 Service 가 생기면 그 값으로)
*  */
@Component
@RequiredArgsConstructor
public class LevelCheckInterceptor implements HandlerInterceptor {

    private final GrowthService growthService;   // 레벨 조회

    // 컨트롤러 도착 "전"에 실행
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // 컨트롤러 메서드가 아니면(정적 파일 등) 검사하지 않고 통과
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // @RequireLevel 찾기 : 메서드에 없으면 클래스에서 찾는다
        RequireLevel requireLevel = handlerMethod.getMethodAnnotation(RequireLevel.class);
        if (requireLevel == null) {
            requireLevel = handlerMethod.getBeanType().getAnnotation(RequireLevel.class);
        }

        // 어노테이션이 없으면 레벨 조건이 없는 API → 그냥 통과 (청약통장·당첨 결과 등)
        if (requireLevel == null) {
            return true;
        }

        // 필요한 레벨과 지금 레벨 비교
        int requiredLevel = requireLevel.value().getDefaultLevel();
        int currentLevel = findCurrentLevel();

        // 레벨이 모자라면 업무 예외 → GlobalExceptionHandler 가 403 LEVEL_LOCKED 로 응답
        if (currentLevel < requiredLevel) {
            throw new BusinessException(CommonErrorCode.LEVEL_LOCKED);
        }

        return true;
    }

    /* comment.
    *   로그인 회원의 지금 레벨 (매번 DB 값으로 확인, SP-24)
    *   - 로그인 정보는 Security 가 SecurityContextHolder 에 넣어 둔다 (강의 10_Spring-Security)
    *   - 로그인 안 한 요청은 Security 가 이미 401 로 막으므로 여기까지 오지 않는다. 그래도 정보가 없으면 0 (막는 쪽)
    *  */
    private int findCurrentLevel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthDetails details)) {
            return 0;
        }
        return growthService.getLevel(details.getMemberId());
    }
}
