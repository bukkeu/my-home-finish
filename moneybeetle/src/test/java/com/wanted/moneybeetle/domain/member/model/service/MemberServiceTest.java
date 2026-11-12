package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;

import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberService 1차 - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class MemberServiceTest {

    private MemberRepository memberRepository;
    private TermsRepository termsRepository;
    private MemberTermsAgreementRepository memberTermsAgreementRepository;
    private PasswordEncoder passwordEncoder;
    private ForbiddenWordService forbiddenWordService;
    private Clock clock;
    private com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository socialAccountRepository;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        termsRepository = mock(TermsRepository.class);
        memberTermsAgreementRepository = mock(MemberTermsAgreementRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        socialAccountRepository = mock(com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository.class);

        clock = Clock.fixed(
                Instant.parse("2026-11-08T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        memberService = new MemberService(
                memberRepository,
                termsRepository,
                memberTermsAgreementRepository,
                passwordEncoder,
                forbiddenWordService,
                clock,
                org.mockito.Mockito.mock(com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository.class),
                socialAccountRepository
        );
    }

    @Test
    @DisplayName("이메일 중복 확인은 대소문자를 구분하지 않는다 (소문자로 바꿔 조회)")
    void emailCaseInsensitive() {
        when(memberRepository.existsByEmail("minji@gmail.com")).thenReturn(true);

        assertThat(memberService.isEmailAvailable("MinJi@Gmail.com").isAvailable()).isFalse();
        verify(memberRepository).existsByEmail("minji@gmail.com");
        assertThat(memberService.isEmailAvailable("new@gmail.com").isAvailable()).isTrue();
    }

    @Test
    @DisplayName("빈 이메일은 쓸 수 없다")
    void blankEmail() {
        assertThat(memberService.isEmailAvailable(" ").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable(null).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("형식이 틀리거나 100자를 넘는 이메일은 조회하지 않고 쓸 수 없다고 답한다")
    void invalidEmailIsNotAvailable() {
        String tooLong = "user@" + "d".repeat(60) + "." + "e".repeat(31) + ".com";   // 101자

        assertThat(tooLong).hasSize(101);
        assertThat(memberService.isEmailAvailable("not-an-email").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable("minji@").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable(tooLong).isAvailable()).isFalse();
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("앞뒤 공백이 붙은 원본은 정규화하면 올바르더라도 쓸 수 없다 (가입 요청은 원본 그대로 검증하므로)")
    void paddedRawEmailIsNotAvailable() {
        assertThat(memberService.isEmailAvailable(" minji@gmail.com").isAvailable()).isFalse();
        assertThat(memberService.isEmailAvailable("minji@gmail.com ").isAvailable()).isFalse();
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("100자 이하의 올바른 이메일은 중복만 본다")
    void maxLengthEmailChecksDuplicate() {
        String max = "user@" + "d".repeat(60) + "." + "e".repeat(30) + ".com";   // 정확히 100자

        assertThat(max).hasSize(100);
        assertThat(memberService.isEmailAvailable(max).isAvailable()).isTrue();
        verify(memberRepository).existsByEmail(max);
    }

    private Member newMember() {
        return Member.signup("minji@gmail.com", "$2a$10$hash", "민지", java.time.LocalDate.of(1999, 4, 12), null,
                java.time.LocalDate.of(2026, 11, 8));
    }

    @Test
    @DisplayName("잔액 조회는 회원의 레벨·경험치·캐시를 돌려주고, 없는 회원은 MEMBER_NOT_FOUND")
    void getBalance() {
        Member member = newMember();
        member.addReward(12_000, 1_250);
        when(memberRepository.findById(8L)).thenReturn(java.util.Optional.of(member));

        MemberBalanceDTO balance = memberService.getBalance(8L);
        assertThat(balance.getCash()).isEqualTo(12_000);
        assertThat(balance.getExp()).isEqualTo(1_250);

        assertThatThrownBy(() -> memberService.getBalance(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("잠금 조회·보상 반영·캐시 차감은 회원 행을 잠그고(findByIdForUpdate) 읽는다")
    void lockedUpdates() {
        Member member = newMember();
        when(memberRepository.findByIdForUpdate(8L)).thenReturn(java.util.Optional.of(member));

        assertThat(memberService.lockBalance(8L).getCash()).isZero();
        assertThat(memberService.addReward(8L, 30_000, 100).getCash()).isEqualTo(30_000);
        assertThat(memberService.deductCash(8L, 10_000).getCash()).isEqualTo(20_000);
        assertThat(member.getExp()).isEqualTo(100);
        verify(memberRepository, org.mockito.Mockito.times(3)).findByIdForUpdate(8L);

        assertThatThrownBy(() -> memberService.deductCash(8L, 999_999)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memberService.lockBalance(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("닉네임은 형식이 맞고 중복이 아닐 때만 쓸 수 있다")
    void nickname() {
        when(memberRepository.existsByNickname("민지")).thenReturn(true);

        assertThat(memberService.isNicknameAvailable("민지").isAvailable()).isFalse();
        assertThat(memberService.isNicknameAvailable("새닉네임").isAvailable()).isTrue();

        assertThatThrownBy(() -> memberService.isNicknameAvailable("!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.NICKNAME_INVALID);
    }

    @Test
    @DisplayName("약관 목록은 번호순으로 필수 여부와 함께 돌려준다")
    void agreements() {
        Terms t1 = Terms.create("서비스 이용약관", "본문1", true, "1.0", LocalDateTime.of(2026, 11, 1, 0, 0));
        Terms t3 = Terms.create("마케팅 정보 수신", "본문3", false, "1.0", LocalDateTime.of(2026, 11, 1, 0, 0));
        when(termsRepository.findAllByOrderByTermsIdAsc()).thenReturn(List.of(t1, t3));

        List<TermsDTO> result = memberService.findAgreements();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("서비스 이용약관");
        assertThat(result.get(0).isRequired()).isTrue();
        assertThat(result.get(1).isRequired()).isFalse();
    }

    @Test
    @DisplayName("회원가입 성공 시 회원 저장, 비밀번호 암호화, 약관 동의 기록을 저장한다")
    void signupSuccess() {
        // given
        Terms requiredTerms = Terms.create(
                "서비스 이용약관",
                "본문1",
                true,
                "1.0",
                LocalDateTime.of(2026, 11, 1, 0, 0)
        );

        Terms optionalTerms = Terms.create(
                "마케팅 정보 수신",
                "본문2",
                false,
                "1.0",
                LocalDateTime.of(2026, 11, 1, 0, 0)
        );

        ReflectionTestUtils.setField(requiredTerms, "termsId", 1L);
        ReflectionTestUtils.setField(optionalTerms, "termsId", 2L);

        when(termsRepository.findByRequiredTrue())
                .thenReturn(List.of(requiredTerms));

        when(termsRepository.findByTermsIdIn(Set.of(1L, 2L)))
                .thenReturn(List.of(requiredTerms, optionalTerms));

        when(passwordEncoder.encode("beetle1234"))
                .thenReturn("encoded-password");

        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "memberId", 100L);
                    return member;
                });

        SignupDTO request = new SignupDTO(
                "MinJi@Gmail.com",
                "beetle1234",
                "beetle1234",
                "민지",
                java.time.LocalDate.of(1999, 4, 12),
                "FEMALE",
                List.of(1L, 2L)
        );

        // when
        SignupResultDTO result = memberService.signup(request);

        // then
        assertThat(result.getMemberId()).isEqualTo(100L);
        assertThat(result.getNickname()).isEqualTo("민지");

        verify(passwordEncoder).encode("beetle1234");
        verify(memberRepository).saveAndFlush(any(Member.class));
        verify(memberTermsAgreementRepository).saveAll(any());

        verify(forbiddenWordService).check("민지");
    }

    /* ---------- 회원가입 실패 경우 (저장은 하지 않는다) ---------- */

    private static final long REQUIRED_TERMS_ID = 1L;
    private static final long OPTIONAL_TERMS_ID = 2L;

    private Terms termsWithId(boolean required, long id) {
        Terms terms = Terms.create("약관 " + id, "본문", required, "1.0", LocalDateTime.of(2026, 11, 1, 0, 0));
        ReflectionTestUtils.setField(terms, "termsId", id);
        return terms;
    }

    /** 필수 약관 1번, 선택 약관 2번이 있는 상태로 둔다 */
    private void givenTerms() {
        Terms required = termsWithId(true, REQUIRED_TERMS_ID);
        Terms optional = termsWithId(false, OPTIONAL_TERMS_ID);
        when(termsRepository.findByRequiredTrue()).thenReturn(List.of(required));
        when(termsRepository.findByTermsIdIn(Set.of(REQUIRED_TERMS_ID, OPTIONAL_TERMS_ID)))
                .thenReturn(List.of(required, optional));
        when(termsRepository.findByTermsIdIn(Set.of(REQUIRED_TERMS_ID))).thenReturn(List.of(required));
        when(termsRepository.findByTermsIdIn(Set.of(OPTIONAL_TERMS_ID))).thenReturn(List.of(optional));
        when(passwordEncoder.encode("beetle1234")).thenReturn("encoded-password");
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "memberId", 100L);
            return member;
        });
    }

    private SignupDTO signupRequest(String email, String password, String passwordConfirm, String nickname,
                                    java.time.LocalDate birthDate, List<Long> termsIds) {
        return new SignupDTO(email, password, passwordConfirm, nickname, birthDate, "FEMALE", termsIds);
    }

    private SignupDTO validRequest() {
        return signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID));
    }

    private void assertSignupFails(SignupDTO request, MemberErrorCode expected) {
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verify(memberTermsAgreementRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_DUPLICATED 이고 저장하지 않는다 (대소문자 구분 없이 소문자로 조회)")
    void signupFailsWhenEmailDuplicated() {
        givenTerms();
        when(memberRepository.existsByEmail("minji@gmail.com")).thenReturn(true);

        assertSignupFails(signupRequest("MinJi@Gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 EMAIL_INVALID 이고 중복 조회도 하지 않는다")
    void signupFailsWhenEmailInvalid() {
        givenTerms();

        assertSignupFails(signupRequest("not-an-email", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.EMAIL_INVALID);
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 NICKNAME_DUPLICATED")
    void signupFailsWhenNicknameDuplicated() {
        givenTerms();
        when(memberRepository.existsByNickname("민지")).thenReturn(true);

        assertSignupFails(validRequest(), MemberErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    @DisplayName("닉네임 형식이 틀리면 NICKNAME_INVALID 이고 금칙어·중복 검사는 하지 않는다")
    void signupFailsWhenNicknameInvalid() {
        givenTerms();

        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "!",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.NICKNAME_INVALID);
        verify(forbiddenWordService, never()).check(anyString());
        verify(memberRepository, never()).existsByNickname(anyString());
    }

    @Test
    @DisplayName("닉네임에 금칙어가 있으면 FORBIDDEN_WORD 예외가 그대로 나가고 저장하지 않는다")
    void signupFailsWhenNicknameHasForbiddenWord() {
        givenTerms();
        org.mockito.Mockito.doThrow(new BusinessException(CommonErrorCode.FORBIDDEN_WORD))
                .when(forbiddenWordService).check("민지");

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN_WORD));
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("비밀번호 형식이 틀리면 PASSWORD_INVALID, 비밀번호 확인이 다르면 PASSWORD_MISMATCH")
    void signupFailsWhenPasswordWrong() {
        givenTerms();

        assertSignupFails(signupRequest("minji@gmail.com", "short", "short", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.PASSWORD_INVALID);
        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle9999", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("오늘(서울 2026-11-08) 기준 만 14세 생일 전날이면 AGE_UNDER_14, 미래 날짜면 BIRTH_DATE_INVALID")
    void signupFailsWhenAgeOrBirthDateWrong() {
        givenTerms();

        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(2012, 11, 9), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.AGE_UNDER_14);
        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(2026, 11, 9), List.of(REQUIRED_TERMS_ID)), MemberErrorCode.BIRTH_DATE_INVALID);
    }

    @Test
    @DisplayName("오늘이 만 14세 생일이면 가입할 수 있다 (경계)")
    void signupSucceedsOnFourteenthBirthday() {
        givenTerms();

        SignupResultDTO result = memberService.signup(signupRequest("minji@gmail.com", "beetle1234", "beetle1234",
                "민지", java.time.LocalDate.of(2012, 11, 8), List.of(REQUIRED_TERMS_ID)));

        assertThat(result.getMemberId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않았거나 동의 목록이 비어 있으면 REQUIRED_TERMS_NOT_AGREED")
    void signupFailsWhenRequiredTermsNotAgreed() {
        givenTerms();

        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(OPTIONAL_TERMS_ID)),
                MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of()), MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), java.util.Arrays.asList(REQUIRED_TERMS_ID, null)),
                MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    @DisplayName("존재하지 않는 약관 ID가 섞여 있으면 TERMS_NOT_FOUND")
    void signupFailsWhenTermsNotFound() {
        givenTerms();
        when(termsRepository.findByTermsIdIn(Set.of(REQUIRED_TERMS_ID, 99L)))
                .thenReturn(List.of(termsWithId(true, REQUIRED_TERMS_ID)));

        assertSignupFails(signupRequest("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                java.time.LocalDate.of(1999, 4, 12), List.of(REQUIRED_TERMS_ID, 99L)), MemberErrorCode.TERMS_NOT_FOUND);
    }

    /* ---------- 동시 가입: 중복 확인을 함께 통과해도 DB 고유 제약이 막는다 ---------- */

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException(
                        "Duplicate entry", new java.sql.SQLException("Duplicate entry"), constraintName));
    }

    @Test
    @DisplayName("저장 시점에 uk_member_email 위반이면 EMAIL_DUPLICATED(409)로 바꾸고 약관 동의는 저장하지 않는다 (MySQL 의 테이블.제약 형태도)")
    void signupMapsEmailUniqueViolation() {
        givenTerms();
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(uniqueViolation("member.uk_member_email"));

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_DUPLICATED);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(409);
                });
        verify(memberTermsAgreementRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("저장 시점에 uk_member_nickname 위반이면 NICKNAME_DUPLICATED(409)로 바꾼다")
    void signupMapsNicknameUniqueViolation() {
        givenTerms();
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(uniqueViolation("uk_member_nickname"));

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("다른 제약 위반이나 제약 이름을 알 수 없는 무결성 오류는 바꾸지 않고 그대로 던진다 (메시지에 email 이 있어도)")
    void signupRethrowsOtherIntegrityErrors() {
        givenTerms();
        DataIntegrityViolationException other = uniqueViolation("uk_something_else");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(other);
        assertThatThrownBy(() -> memberService.signup(validRequest())).isSameAs(other);

        DataIntegrityViolationException unknown =
                new DataIntegrityViolationException("Duplicate entry 'minji@gmail.com' for key 'email'");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(unknown);
        assertThatThrownBy(() -> memberService.signup(validRequest())).isSameAs(unknown);
        verify(memberTermsAgreementRepository, never()).saveAll(any());
    }

    /* ---------- findNicknames (이슈 #145, 다른 도메인용) ---------- */

    private Member memberWithId(long id, String nickname, String status) {
        Member member = Member.signup("m" + id + "@b.com", "hash", nickname,
                java.time.LocalDate.of(1999, 4, 12), null, java.time.LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(member, "memberId", id);
        ReflectionTestUtils.setField(member, "status", MemberStatus.valueOf(status));
        return member;
    }

    @Test
    @DisplayName("findNicknames: ID 여러 개를 한 번의 조회로 받아 ID → 닉네임 맵을 돌려준다 (N+1 방지)")
    void findNicknamesInOneQuery() {
        when(memberRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(
                memberWithId(1L, "민지", MemberStatus.ACTIVE.name()), memberWithId(2L, "도현", MemberStatus.ACTIVE.name())));

        java.util.Map<Long, String> result = memberService.findNicknames(List.of(1L, 2L));

        assertThat(result).containsOnly(
                java.util.Map.entry(1L, "민지"), java.util.Map.entry(2L, "도현"));
        verify(memberRepository, org.mockito.Mockito.times(1)).findAllById(any());
    }

    @Test
    @DisplayName("findNicknames: 없는 ID 는 결과에서 빠지고, null·중복 ID 는 걸러서 조회한다")
    void findNicknamesSkipsMissingNullAndDuplicates() {
        when(memberRepository.findAllById(Set.of(1L, 99L))).thenReturn(List.of(memberWithId(1L, "민지", MemberStatus.ACTIVE.name())));

        java.util.Map<Long, String> result =
                memberService.findNicknames(java.util.Arrays.asList(1L, null, 1L, 99L));

        assertThat(result).containsOnly(java.util.Map.entry(1L, "민지"));   // 99 는 없는 회원이라 빠진다
        verify(memberRepository).findAllById(Set.of(1L, 99L));            // null 과 중복은 걸러진 ID 만 넘어간다
    }

    @Test
    @DisplayName("findNicknames: 빈 목록·null·null 만 있는 목록이면 조회하지 않고 빈 맵이다")
    void findNicknamesEmptyInputDoesNotQuery() {
        assertThat(memberService.findNicknames(List.of())).isEmpty();
        assertThat(memberService.findNicknames(null)).isEmpty();
        assertThat(memberService.findNicknames(java.util.Collections.singletonList(null))).isEmpty();

        verify(memberRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("findNicknames: 탈퇴한 회원은 닉네임 대신 '탈퇴한 사용자', 정지된 회원은 그대로 (DP-06)")
    void findNicknamesMasksWithdrawnMembers() {
        when(memberRepository.findAllById(Set.of(1L, 2L, 3L))).thenReturn(List.of(
                memberWithId(1L, "민지", MemberStatus.WITHDRAWN.name()),
                memberWithId(2L, "도현", MemberStatus.SUSPENDED.name()),
                memberWithId(3L, "하린", MemberStatus.ACTIVE.name())));

        java.util.Map<Long, String> result = memberService.findNicknames(List.of(1L, 2L, 3L));

        assertThat(result.get(1L)).isEqualTo("탈퇴한 사용자");
        assertThat(result.get(2L)).isEqualTo("도현");
        assertThat(result.get(3L)).isEqualTo("하린");
    }

    @Test
    @DisplayName("findNicknames: 돌려준 맵은 수정할 수 없다")
    void findNicknamesResultIsUnmodifiable() {
        when(memberRepository.findAllById(Set.of(1L))).thenReturn(List.of(memberWithId(1L, "민지", MemberStatus.ACTIVE.name())));

        java.util.Map<Long, String> result = memberService.findNicknames(List.of(1L));

        assertThatThrownBy(() -> result.put(2L, "x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
