package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SocialSignupDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.SocialAccount;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberService 간편 로그인 가입·조회 (이슈 #9) - Repository 를 가짜로 두고 서버·DB 없이 돈다
*  */
class MemberSocialSignupTest {

    private MemberRepository memberRepository;
    private TermsRepository termsRepository;
    private MemberTermsAgreementRepository agreementRepository;
    private SocialAccountRepository socialAccountRepository;
    private ForbiddenWordService forbiddenWordService;
    private MemberService memberService;
    private SocialSignupDTO request;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        termsRepository = mock(TermsRepository.class);
        agreementRepository = mock(MemberTermsAgreementRepository.class);
        socialAccountRepository = mock(SocialAccountRepository.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        memberService = new MemberService(memberRepository, termsRepository, agreementRepository, mock(PasswordEncoder.class),
                forbiddenWordService, Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(InstructorProfileRepository.class), socialAccountRepository);

        Terms required = Terms.create("서비스 이용약관", "내용", true, "1.0", LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(required, "termsId", 1L);
        when(termsRepository.findByTermsIdIn(any())).thenReturn(List.of(required));
        when(termsRepository.findByRequiredTrue()).thenReturn(List.of(required));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "memberId", 42L);
            return saved;
        });
        request = new SocialSignupDTO("민지", LocalDate.of(1999, 4, 12), "FEMALE", List.of(1L));
    }

    private static void assertCode(Runnable action, MemberErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("가입하면 비밀번호 없이, 본인인증을 마친 회원으로 저장하고 약관 동의와 간편 로그인 연결을 함께 저장한다")
    void signupSocial() {
        SignupResultDTO result = memberService.signupSocial(request, "Minji@Gmail.com", "GOOGLE", "g-1");

        assertThat(result.getMemberId()).isEqualTo(42L);
        ArgumentCaptor<Member> member = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(member.capture());
        assertThat(member.getValue().getEmail()).isEqualTo("minji@gmail.com");       // 소문자로 저장
        assertThat(member.getValue().getPasswordHash()).isNull();                      // 간편 로그인만 쓰는 계정
        assertThat(member.getValue().isIdentityVerified()).isTrue();
        assertThat(member.getValue().getRole()).isEqualTo(Member.ROLE_MEMBER);
        verify(agreementRepository).saveAll(any());
        ArgumentCaptor<SocialAccount> account = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(account.capture());
        assertThat(account.getValue().getMemberId()).isEqualTo(42L);
        assertThat(account.getValue().getProvider()).isEqualTo("GOOGLE");
        assertThat(account.getValue().getProviderUserId()).isEqualTo("g-1");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_DUPLICATED, 저장하지 않는다")
    void duplicatedEmail() {
        when(memberRepository.existsByEmail("minji@gmail.com")).thenReturn(true);

        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.EMAIL_DUPLICATED);
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("닉네임이 중복이면 NICKNAME_DUPLICATED, 형식이 틀리면 NICKNAME_INVALID, 금칙어 검사도 한다")
    void nicknameRules() {
        when(memberRepository.existsByNickname("민지")).thenReturn(true);
        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.NICKNAME_DUPLICATED);

        request.setNickname("!");
        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.NICKNAME_INVALID);

        request.setNickname("민지");
        when(memberRepository.existsByNickname("민지")).thenReturn(false);
        memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1");
        verify(forbiddenWordService, org.mockito.Mockito.atLeastOnce()).check("민지");
    }

    @Test
    @DisplayName("만 14세 미만이면 AGE_UNDER_14, 미래 생년월일이면 BIRTH_DATE_INVALID")
    void birthDateRules() {
        request.setBirthDate(LocalDate.of(2015, 1, 1));
        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.AGE_UNDER_14);

        request.setBirthDate(LocalDate.of(2027, 1, 1));
        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.BIRTH_DATE_INVALID);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않았으면 REQUIRED_TERMS_NOT_AGREED")
    void requiredTerms() {
        request.setAgreedTermsIds(List.of());

        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    @DisplayName("같은 제공자 계정이 동시에 가입해 고유 제약에 걸리면 OAUTH_FAILED (가입은 되돌려진다)")
    void socialAccountUniqueViolation() {
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class))).thenThrow(new DataIntegrityViolationException("uk_social_account_1"));

        assertCode(() -> memberService.signupSocial(request, "minji@gmail.com", "GOOGLE", "g-1"), MemberErrorCode.OAUTH_FAILED);
    }

    @Test
    @DisplayName("제공자 계정에 연결된 회원을 찾는다. 연결이 없으면 비어 있다")
    void findBySocial() {
        Member member = Member.signupSocial("minji@gmail.com", "민지", LocalDate.of(1999, 4, 12), null, LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(member, "memberId", 42L);
        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "g-1"))
                .thenReturn(Optional.of(SocialAccount.create(42L, "GOOGLE", "g-1")));
        when(memberRepository.findById(42L)).thenReturn(Optional.of(member));

        Optional<LoginMemberDTO> found = memberService.findLoginMemberBySocial("GOOGLE", "g-1");

        assertThat(found).isPresent();
        assertThat(found.get().getMemberId()).isEqualTo(42L);
        assertThat(found.get().getPasswordHash()).isNull();
        assertThat(memberService.findLoginMemberBySocial("KAKAO", "none")).isEmpty();
        verify(socialAccountRepository).findByProviderAndProviderUserId("GOOGLE", "g-1");
    }

    @Test
    @DisplayName("SocialAccount 는 GOOGLE·KAKAO 외 제공자나 빈 사용자 ID 를 받지 않는다")
    void socialAccountRules() {
        assertThatThrownBy(() -> SocialAccount.create(1L, "NAVER", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocialAccount.create(1L, "GOOGLE", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocialAccount.create(null, "GOOGLE", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
