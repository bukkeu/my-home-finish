package com.wanted.moneybeetle.global.security;

import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   LevelCheckInterceptor 확인 (스프링 없이 단독 실행)
*   - @RequireLevel 이 없으면 통과 (청약통장·당첨 결과처럼 레벨 조건이 없는 API)
*   - 붙어 있으면 레벨 검사 : 지금은 회원 레벨 조회(김예린 연동) 전이라 0 으로 보고 LEVEL_LOCKED
*  */
class LevelCheckInterceptorTest {

    private final LevelCheckInterceptor interceptor = new LevelCheckInterceptor();
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

    private HandlerMethod handler(Object controller, String method) throws NoSuchMethodException {
        return new HandlerMethod(controller, controller.getClass().getMethod(method));
    }

    @Test
    void 컨트롤러_메서드가_아니면_통과() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void RequireLevel이_없으면_통과() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler(new HousingController(), "account"))).isTrue();
    }

    @Test
    void 메서드에_RequireLevel이_있고_레벨이_모자라면_LEVEL_LOCKED() {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler(new HousingController(), "complexes")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.LEVEL_LOCKED);
    }

    @Test
    void 클래스에_RequireLevel이_있어도_검사한다() {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler(new TradingController(), "home")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 해금_레벨_기본값은_모의투자_8_모의청약_12() {
        assertThat(UnlockFeature.TRADING.getDefaultLevel()).isEqualTo(8);
        assertThat(UnlockFeature.HOUSING.getDefaultLevel()).isEqualTo(12);
        assertThat(UnlockFeature.TRADING.getSettingKey()).isEqualTo("UNLOCK_LEVEL_TRADING");
    }
}
