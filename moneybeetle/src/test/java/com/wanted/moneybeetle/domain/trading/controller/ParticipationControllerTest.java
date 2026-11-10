package com.wanted.moneybeetle.domain.trading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.trading.model.service.ParticipationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : PM (#23)
*   ParticipationController 홈·시즌 참가 확인 (서버·DB 없이 Service 는 가짜(mock))
*   - 로그인 회원 번호를 세션(@AuthenticationPrincipal)에서 꺼내 Service 에 넘긴다
*   - 로그인 정보가 없으면 null 을 넘긴다 (홈은 참가 정보 없이, 참가는 Service 가 401 로 막는다)
*  */
class ParticipationControllerTest {

    private ParticipationService participationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        participationService = mock(ParticipationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ParticipationController(participationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long memberId) {
        AuthDetails me = mock(AuthDetails.class);
        when(me.getMemberId()).thenReturn(memberId);
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(me, null, List.of())));
    }

    @Test
    void 홈과_참가는_로그인한_회원의_번호를_넘긴다() throws Exception {
        loginAs(8L);

        mockMvc.perform(get("/api/v1/trading/home")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/trading/seasons/7/participations")).andExpect(status().isCreated());

        verify(participationService).getHome(8L);
        verify(participationService).join(7L, 8L);
    }

    @Test
    void 로그인_정보가_없으면_회원_번호는_null() throws Exception {
        mockMvc.perform(get("/api/v1/trading/home")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/trading/seasons/7/participations")).andExpect(status().isCreated());

        verify(participationService).getHome(null);
        verify(participationService).join(7L, null);
    }
}
