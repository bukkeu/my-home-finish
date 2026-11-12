package com.wanted.moneybeetle.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationDTO;
import com.wanted.moneybeetle.domain.notification.model.dto.NotificationListDTO;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.domain.notification.model.entity.NotificationType;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {

    private NotificationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", 8L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("알림 목록: 안 읽은 수와 items 를 isRead 필드명으로 돌려준다 (API 25)")
    void getsNotifications() throws Exception {
        Notification notification = Notification.create(8L, NotificationType.INQUIRY_REPLY,
                "문의에 답변이 등록됐어요", "/user/inquiry/3");
        ReflectionTestUtils.setField(notification, "notificationId", 5L);
        when(service.findNotifications(eq(8L), any(Pageable.class)))
                .thenReturn(new NotificationListDTO(2, List.of(NotificationDTO.from(notification))));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.items[0].notificationId").value(5))
                .andExpect(jsonPath("$.data.items[0].type").value("INQUIRY_REPLY"))
                .andExpect(jsonPath("$.data.items[0].linkUrl").value("/user/inquiry/3"))
                .andExpect(jsonPath("$.data.items[0].isRead").value(false));
    }

    @Test
    @DisplayName("쪽 번호·크기는 기본 0·20, 크기는 최대 50, 음수는 보정한다")
    void clampsPaging() throws Exception {
        when(service.findNotifications(eq(8L), any(Pageable.class))).thenReturn(new NotificationListDTO(0, List.of()));

        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications").param("page", "-4").param("size", "999")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications").param("page", "2").param("size", "0")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service, org.mockito.Mockito.times(3)).findNotifications(eq(8L), pageable.capture());
        var all = pageable.getAllValues();
        org.assertj.core.api.Assertions.assertThat(all.get(0)).isEqualTo(org.springframework.data.domain.PageRequest.of(0, 20));
        org.assertj.core.api.Assertions.assertThat(all.get(1)).isEqualTo(org.springframework.data.domain.PageRequest.of(0, 50));
        org.assertj.core.api.Assertions.assertThat(all.get(2)).isEqualTo(org.springframework.data.domain.PageRequest.of(2, 1));
    }

    @Test
    @DisplayName("알림 읽음: 로그인한 회원 번호로 서비스를 부르고 204 (API 26)")
    void marksRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/5/read")).andExpect(status().isNoContent());

        verify(service).markRead(8L, 5L);
    }

    @Test
    @DisplayName("남의 알림이거나 없는 알림이면 404")
    void markReadNotFound() throws Exception {
        doThrow(new BusinessException(CommonErrorCode.NOT_FOUND)).when(service).markRead(8L, 99L);

        mockMvc.perform(patch("/api/v1/notifications/99/read")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("알림 모두 읽음: 204 (API 27)")
    void marksAllRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isNoContent());

        verify(service).markAllRead(8L);
    }
}
