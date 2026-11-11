package com.wanted.moneybeetle.global.exception;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.support.MockSecurityServices;
import com.wanted.moneybeetle.support.SecurityTestApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #142 GlobalExceptionHandler + 실제 SecurityConfig 를 함께 띄워, 컨트롤러에서 난 인증·권한 예외가
*   보안 필터(ExceptionTranslationFilter → SecurityErrorHandler)를 거쳐 401·403 JSON 으로 나가는지 확인한다
*   (GlobalExceptionHandlerTest 는 필터 없이 "다시 던지는지"까지만 본다)
*   - 로그인한 회원으로 요청해 필터가 컨트롤러보다 먼저 막지 않게 한다. DB 는 쓰지 않는다
*  */
@SpringBootTest(classes = {SecurityTestApp.class,
        GlobalExceptionSecurityFlowTest.ThrowingController.class, GlobalExceptionHandler.class})
@MockSecurityServices        // 로그인 핸들러·계정 상태 확인이 쓰는 서비스는 가짜 (#161)
class GlobalExceptionSecurityFlowTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/api/v1/test/denied")
        public String denied() {
            throw new AccessDeniedException("관리자만");
        }

        @GetMapping("/api/v1/test/unauthenticated")
        public String unauthenticated() {
            throw new BadCredentialsException("인증 실패");
        }
    }

    /** ActiveMemberFilter 가 쓰는 계정 상태 확인 (@MockSecurityServices 의 가짜). 이 테스트의 회원은 정상 계정이다 */
    @Autowired
    private MemberService memberService;

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(memberService.isActiveMember(anyLong())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // 회원 8번(MEMBER)으로 로그인한 세션
    private AuthDetails member() {
        AuthDetails details = mock(AuthDetails.class);
        when(details.getMemberId()).thenReturn(8L);
        when(details.getUsername()).thenReturn("minji@moneybeetle.local");
        doReturn(List.of(new SimpleGrantedAuthority("MEMBER"))).when(details).getAuthorities();
        return details;
    }

    @Test
    void 컨트롤러의_권한_예외는_보안_필터를_거쳐_403_FORBIDDEN() throws Exception {
        mockMvc.perform(get("/api/v1/test/denied").with(user(member())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 컨트롤러의_인증_예외는_보안_필터를_거쳐_401() throws Exception {
        mockMvc.perform(get("/api/v1/test/unauthenticated").with(user(member())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
