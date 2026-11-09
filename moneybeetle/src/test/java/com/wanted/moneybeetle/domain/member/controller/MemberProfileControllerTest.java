package com.wanted.moneybeetle.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.ProfileImageDTO;
import com.wanted.moneybeetle.domain.member.model.dto.PublicProfileDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.service.MemberProfileService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberController 내 정보·설정·프로필 이미지·공개 프로필 (#7, API 13·14·15·18) - 서버·DB 없이 MockMvc 로 돈다
*  */
class MemberProfileControllerTest {

    private final MemberService memberService = mock(MemberService.class);
    private final MemberProfileService memberProfileService = mock(MemberProfileService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 8));
        ReflectionTestUtils.setField(member, "memberId", 7L);
        AuthDetails principal = new AuthDetails(LoginMemberDTO.from(member, Member.ROLE_MEMBER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService,
                mock(com.wanted.moneybeetle.domain.member.model.service.AccountService.class), memberProfileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("설정 수정은 로그인한 회원의 번호로 서비스에 넘기고 이메일 필드는 받지 않는다")
    void patchMe() throws Exception {
        when(memberService.updateProfile(eq(7L), any(MemberUpdateDTO.class)))
                .thenReturn(new MemberUpdateResultDTO(7L, "민지짱"));

        mockMvc.perform(patch("/api/v1/members/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"민지짱\",\"birthDate\":\"1999-04-12\",\"gender\":\"FEMALE\",\"email\":\"x@x.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.nickname").value("민지짱"));
    }

    @Test
    @DisplayName("닉네임 형식이 틀리면 서비스를 부르지 않는다 (응답 코드는 @Valid 예외 처리 #142 가 정한다)")
    void patchMeInvalidNickname() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"a\"}"));

        verify(memberService, never()).updateProfile(any(), any());
    }

    @Test
    @DisplayName("프로필 이미지는 multipart file 로 받아 주소를 돌려준다")
    void uploadProfileImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2});
        when(memberProfileService.changeProfileImage(eq(7L), any()))
                .thenReturn(new ProfileImageDTO("/uploads/abc.png"));

        mockMvc.perform(multipart("/api/v1/members/me/profile-image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value("/uploads/abc.png"));
    }

    @Test
    @DisplayName("공개 프로필 응답에는 개인정보 필드가 없다")
    void publicProfile() throws Exception {
        when(memberProfileService.getPublicProfile(2L))
                .thenReturn(new PublicProfileDTO(2L, "복리왕", null, 15, "아파트", List.of()));

        mockMvc.perform(get("/api/v1/members/2/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("복리왕"))
                .andExpect(jsonPath("$.data.level").value(15))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.birthDate").doesNotExist())
                .andExpect(jsonPath("$.data.gender").doesNotExist());
    }

    @Test
    @DisplayName("없는 회원의 공개 프로필은 404 MEMBER_NOT_FOUND")
    void publicProfileNotFound() throws Exception {
        when(memberProfileService.getPublicProfile(99L))
                .thenThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/members/99/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }
}
