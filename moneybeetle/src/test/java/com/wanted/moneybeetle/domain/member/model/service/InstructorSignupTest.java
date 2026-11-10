package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#30, API 12 강사 가입 신청)
*   MemberService.signupInstructor (저장 내용·검증)와 MemberSignupService.signupInstructor (본인인증 확인·지우기, 청약통장 없음)를
*   저장소만 가짜로 두고 확인한다. DB 없이 돈다.
*  */
class InstructorSignupTest {

    private MemberRepository memberRepository;
    private TermsRepository termsRepository;
    private InstructorProfileRepository instructorProfileRepository;
    private ForbiddenWordService forbiddenWordService;
    private HousingService housingService;
    private SignupIdentityVerifier identityVerifier;
    private MemberSignupService signupService;
    private final MockHttpSession session = new MockHttpSession();

    private InstructorSignupDTO request;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        termsRepository = mock(TermsRepository.class);
        instructorProfileRepository = mock(InstructorProfileRepository.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        housingService = mock(HousingService.class);
        identityVerifier = mock(SignupIdentityVerifier.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");

        Clock clock = Clock.fixed(Instant.parse("2026-11-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));   // 서울 10:00
        MemberService memberService = new MemberService(memberRepository, termsRepository,
                mock(MemberTermsAgreementRepository.class), passwordEncoder, forbiddenWordService, clock,
                instructorProfileRepository, mock(SocialAccountRepository.class));
        signupService = new MemberSignupService(memberService, housingService, identityVerifier);

        Terms required = Terms.create("서비스 이용약관", "내용", true, "1.0", LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(required, "termsId", 1L);
        when(termsRepository.findByTermsIdIn(any())).thenReturn(List.of(required));
        when(termsRepository.findByRequiredTrue()).thenReturn(List.of(required));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "memberId", 31L);
            return saved;
        });
        when(instructorProfileRepository.save(any(InstructorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        request = new InstructorSignupDTO("Teacher@MoneyBeetle.kr", "teach1234", "teach1234", "박서연", "청약, ETF", List.of(1L));
    }

    @Test
    @DisplayName("강사 가입 신청: 강사명을 닉네임으로 회원을 만들고, 강사 정보를 승인 대기·신청 시각과 함께 저장한다")
    void savesMemberAndPendingProfile() {
        InstructorSignupResultDTO result = signupService.signupInstructor(request, session);

        assertThat(result.getMemberId()).isEqualTo(31L);
        assertThat(result.getApprovalStatus()).isEqualTo(InstructorProfile.PENDING);

        ArgumentCaptor<Member> member = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(member.capture());
        assertThat(member.getValue().getEmail()).isEqualTo("teacher@moneybeetle.kr");   // 이메일은 소문자로 맞춘다
        assertThat(member.getValue().getNickname()).isEqualTo("박서연");
        assertThat(member.getValue().getRole()).isEqualTo(Member.ROLE_MEMBER);        // 승인 전에는 강사 권한이 없다 (SP-07)
        assertThat(member.getValue().getBirthDate()).isNull();
        assertThat(member.getValue().isIdentityVerified()).isTrue();

        ArgumentCaptor<InstructorProfile> profile = ArgumentCaptor.forClass(InstructorProfile.class);
        verify(instructorProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getMemberId()).isEqualTo(31L);
        assertThat(profile.getValue().getDisplayName()).isEqualTo("박서연");
        assertThat(profile.getValue().getField()).isEqualTo("청약, ETF");
        assertThat(profile.getValue().getApprovalStatus()).isEqualTo(InstructorProfile.PENDING);
        assertThat(profile.getValue().getAppliedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));   // 관리자 신청 목록(API 104)에 나온다

        verify(housingService, never()).openAccount(any());   // 강사는 학습자 청약통장을 만들지 않는다
        verify(identityVerifier).clear(session);
    }

    @Test
    @DisplayName("본인인증을 하지 않았으면 회원·강사 정보를 만들지 않고 IDENTITY_NOT_VERIFIED")
    void blocksWhenNotVerified() {
        doThrow(new BusinessException(MemberErrorCode.IDENTITY_NOT_VERIFIED))
                .when(identityVerifier).requireVerified("Teacher@MoneyBeetle.kr", session);

        assertThatThrownBy(() -> signupService.signupInstructor(request, session))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.IDENTITY_NOT_VERIFIED));

        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verify(instructorProfileRepository, never()).save(any(InstructorProfile.class));
        verify(identityVerifier, never()).clear(any());
    }

    @Test
    @DisplayName("강사명이 이미 쓰는 닉네임이면 NICKNAME_DUPLICATED 이고 인증 표시는 남긴다 (다시 인증하지 않아도 된다)")
    void rejectsDuplicatedDisplayName() {
        when(memberRepository.existsByNickname("박서연")).thenReturn(true);

        assertThatThrownBy(() -> signupService.signupInstructor(request, session))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED));

        verify(instructorProfileRepository, never()).save(any(InstructorProfile.class));
        verify(identityVerifier, never()).clear(any());
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_DUPLICATED")
    void rejectsDuplicatedEmail() {
        when(memberRepository.existsByEmail("teacher@moneybeetle.kr")).thenReturn(true);

        assertThatThrownBy(() -> signupService.signupInstructor(request, session))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_DUPLICATED));
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("강사명·전문 분야에 금칙어가 있으면 저장하지 않는다")
    void checksForbiddenWords() {
        doThrow(new BusinessException(MemberErrorCode.NICKNAME_INVALID)).when(forbiddenWordService).check("청약, ETF");

        assertThatThrownBy(() -> signupService.signupInstructor(request, session)).isInstanceOf(BusinessException.class);

        verify(forbiddenWordService).check("박서연");
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }
}
