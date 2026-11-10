package com.wanted.moneybeetle.domain.home.config;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.home.model.service.LayoutService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/* comment.
*   담당 : DBA ② (홈, 이슈 #248)
*   학습자 화면(/user/**)을 그리기 직전에 Model 에 "layout"(LayoutDTO)을 넣는다. 공통 헤더·햄버거 메뉴가 닉네임·레벨·캐시·경험치를 보여 주는 데 쓴다.
*   - 화면(ModelAndView 가 있는 응답)이고 로그인한 학습자(MEMBER)일 때만 넣는다. REST API(@ResponseBody)는 ModelAndView 가 없어 영향이 없다.
*   - 컨트롤러가 이미 layout 을 정해 두었으면 덮어쓰지 않는다. 리다이렉트 응답에도 넣지 않는다.
*   - 값을 만들다 실패하면 로그만 남기고 넘어간다: 템플릿은 layout 이 없어도 기본값으로 그려지므로 헤더 값 하나 때문에 화면 전체가 오류가 되지 않게 한다.
*  */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayoutInterceptor implements HandlerInterceptor {

    public static final String ATTRIBUTE = "layout";
    static final String MEMBER_AUTHORITY = "MEMBER";

    private final LayoutService layoutService;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (modelAndView == null || !modelAndView.hasView() || isRedirect(modelAndView.getViewName())
                || modelAndView.getModel().containsKey(ATTRIBUTE)) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthDetails me) || !isMember(authentication)) {
            return;
        }
        try {
            modelAndView.addObject(ATTRIBUTE, layoutService.getLayout(me.getMemberId()));
        } catch (BusinessException e) {
            // 예상한 경우(탈퇴·정지 계정 등): 오류 코드만 남기고 기본값으로 그린다
            log.warn("공통 레이아웃 값을 만들지 못해 기본값으로 그려요: memberId={}, 오류 코드={}", me.getMemberId(), e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            // 예상 밖의 오류(레벨표 이상, 코드 결함 등): 원인을 찾을 수 있게 스택 트레이스까지 남기고 화면은 기본값으로 그린다
            log.error("공통 레이아웃 값을 만드는 중 예상하지 못한 오류가 나 기본값으로 그려요: memberId={}", me.getMemberId(), e);
        }
    }

    private static boolean isRedirect(String viewName) {
        return viewName != null && (viewName.startsWith("redirect:") || viewName.startsWith("forward:"));
    }

    private static boolean isMember(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (MEMBER_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
