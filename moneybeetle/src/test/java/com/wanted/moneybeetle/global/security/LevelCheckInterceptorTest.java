package com.wanted.moneybeetle.global.security;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   LevelCheckInterceptor 확인 (스프링 없이 단독 실행, 레벨은 가짜 GrowthService 가 알려 준다)
*   - @RequireLevel 이 없으면 레벨을 묻지도 않고 통과 (청약통장·당첨 결과처럼 레벨 조건이 없는 API)
*   - 붙어 있으면 로그인 회원 번호로 GrowthService.getLevel 을 불러 비교 : 모자라면 LEVEL_LOCKED, 같거나 높으면 통과
*   - 로그인 정보가 없으면 0 으로 보고 막는다
*  */
class LevelCheckInterceptorTest {

    private static final Long MEMBER_ID = 8L;

    private final GrowthService growthService = mock(GrowthService.class);
    private final LevelCheckInterceptor interceptor = new LevelCheckInterceptor(growthService);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    static class HousingController {
        public void account() { }                                  // 청약통장 : 레벨 조건 없음

        @RequireLevel(UnlockFeature.HOUSING)
        public void complexes() { }                                // 청약 캘린더 : Lv.12
    }

    @RequireLevel(UnlockFeature.TRADING)
    static class TradingController {
        public void home() { }                                     // 클래스에 붙은 조건 : Lv.8
    }

    @BeforeEach
    void login() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(MEMBER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private HandlerMethod handler(Object controller, String method) throws NoSuchMethodException {
        return new HandlerMethod(controller, controller.getClass().getMethod(method));
    }

    private void assertLocked(HandlerMethod handler) {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.LEVEL_LOCKED);
    }

    @Test
    void 컨트롤러_메서드가_아니면_통과() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void RequireLevel이_없으면_레벨을_묻지_않고_통과() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler(new HousingController(), "account"))).isTrue();
        verify(growthService, never()).getLevel(any());
    }

    @Test
    void 모의청약은_Lv11_이면_LEVEL_LOCKED_Lv12_부터_통과() throws Exception {
        HandlerMethod complexes = handler(new HousingController(), "complexes");

        when(growthService.getLevel(MEMBER_ID)).thenReturn(11);
        assertLocked(complexes);

        when(growthService.getLevel(MEMBER_ID)).thenReturn(12);
        assertThat(interceptor.preHandle(request, response, complexes)).isTrue();
    }

    @Test
    void 클래스에_붙은_조건도_검사한다_모의투자_Lv7_막힘_Lv8_통과() throws Exception {
        HandlerMethod home = handler(new TradingController(), "home");

        when(growthService.getLevel(MEMBER_ID)).thenReturn(7);
        assertLocked(home);

        when(growthService.getLevel(MEMBER_ID)).thenReturn(8);
        assertThat(interceptor.preHandle(request, response, home)).isTrue();
    }

    @Test
    void 로그인_정보가_없으면_막는다() throws Exception {
        SecurityContextHolder.clearContext();
        assertLocked(handler(new HousingController(), "complexes"));
        verify(growthService, never()).getLevel(any());
    }

    @Test
    void 해금_레벨_기본값은_모의투자_8_모의청약_12() {
        assertThat(UnlockFeature.TRADING.getDefaultLevel()).isEqualTo(8);
        assertThat(UnlockFeature.HOUSING.getDefaultLevel()).isEqualTo(12);
        assertThat(UnlockFeature.TRADING.getSettingKey()).isEqualTo("UNLOCK_LEVEL_TRADING");
    }
}
