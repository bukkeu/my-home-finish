package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.MemberProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateResultDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ② (회원·인증)
*   MemberService 내 정보·설정 수정 (#7) - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class MemberProfileUpdateTest {

    private MemberRepository memberRepository;
    private ForbiddenWordService forbiddenWordService;
    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        memberService = new MemberService(memberRepository, mock(TermsRepository.class),
                mock(MemberTermsAgreementRepository.class), mock(PasswordEncoder.class), forbiddenWordService,
                clock, mock(InstructorProfileRepository.class));

        member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 11, 8));
        ReflectionTestUtils.setField(member, "memberId", 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("닉네임·생년월일·성별을 바꾸고 이메일은 그대로다")
    void updatesProfile() {
        when(memberRepository.existsByNickname("민지짱")).thenReturn(false);

        MemberUpdateResultDTO result =
                memberService.updateProfile(1L, new MemberUpdateDTO("민지짱", LocalDate.of(2000, 1, 2), "MALE"));

        assertThat(result.nickname()).isEqualTo("민지짱");
        assertThat(member.getNickname()).isEqualTo("민지짱");
        assertThat(member.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(member.getGender()).isEqualTo("MALE");
        assertThat(member.getEmail()).isEqualTo("minji@gmail.com");
        verify(forbiddenWordService).check("민지짱");
        verify(memberRepository).flush();
    }

    @Test
    @DisplayName("값이 없는 항목은 바꾸지 않는다")
    void nullFieldsAreKept() {
        memberService.updateProfile(1L, new MemberUpdateDTO(null, null, null));

        assertThat(member.getNickname()).isEqualTo("민지");
        assertThat(member.getBirthDate()).isEqualTo(LocalDate.of(1999, 4, 12));
        assertThat(member.getGender()).isEqualTo("FEMALE");
    }

    @Test
    @DisplayName("닉네임을 그대로 두면 중복·금칙어를 다시 검사하지 않는다 (내 닉네임과 중복으로 오인하지 않는다)")
    void sameNicknameSkipsChecks() {
        memberService.updateProfile(1L, new MemberUpdateDTO("민지", null, "MALE"));

        verify(memberRepository, never()).existsByNickname(anyString());
        verify(forbiddenWordService, never()).check(anyString());
        assertThat(member.getGender()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("이미 쓰는 닉네임이면 NICKNAME_DUPLICATED 이고 값은 바뀌지 않는다")
    void duplicatedNickname() {
        when(memberRepository.existsByNickname("복리왕")).thenReturn(true);

        assertThatThrownBy(() -> memberService.updateProfile(1L, new MemberUpdateDTO("복리왕", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED));
        assertThat(member.getNickname()).isEqualTo("민지");
    }

    @Test
    @DisplayName("금칙어가 들어 있으면 FORBIDDEN_WORD 를 그대로 던진다")
    void forbiddenNickname() {
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN_WORD)).when(forbiddenWordService).check("욕설닉");

        assertThatThrownBy(() -> memberService.updateProfile(1L, new MemberUpdateDTO("욕설닉", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN_WORD));
    }

    @Test
    @DisplayName("만 14세 미만 생년월일은 AGE_UNDER_14, 미래 날짜는 BIRTH_DATE_INVALID")
    void invalidBirthDate() {
        assertThatThrownBy(() -> memberService.updateProfile(1L, new MemberUpdateDTO(null, LocalDate.of(2020, 1, 1), null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.AGE_UNDER_14));
        assertThatThrownBy(() -> memberService.updateProfile(1L, new MemberUpdateDTO(null, LocalDate.of(2027, 1, 1), null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.BIRTH_DATE_INVALID));
    }

    @Test
    @DisplayName("동시에 같은 닉네임으로 바꿔 DB 고유 제약에 걸리면 NICKNAME_DUPLICATED 로 바꾼다")
    void concurrentNicknameConflict() {
        when(memberRepository.existsByNickname("복리왕")).thenReturn(false);
        ConstraintViolationException cause =
                new ConstraintViolationException("dup", null, "member.uk_member_nickname");
        doThrow(new DataIntegrityViolationException("dup", cause)).when(memberRepository).flush();

        assertThatThrownBy(() -> memberService.updateProfile(1L, new MemberUpdateDTO("복리왕", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("공개 프로필 원본은 탈퇴·정지 회원이면 MEMBER_NOT_FOUND")
    void inactiveMemberHidden() {
        ReflectionTestUtils.setField(member, "status", Member.STATUS_WITHDRAWN);

        assertThatThrownBy(() -> memberService.getActiveProfile(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        MemberProfileDTO mine = memberService.getProfile(1L);
        assertThat(mine.email()).isEqualTo("minji@gmail.com");
    }
}
