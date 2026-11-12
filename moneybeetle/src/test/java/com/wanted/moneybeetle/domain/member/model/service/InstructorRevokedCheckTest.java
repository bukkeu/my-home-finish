package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/* comment.
*   #410 MemberService.isInstructorRevoked : 문의 도메인이 "지금 승인된 강사인가"를 묻는 메서드.
*   관리자가 강사 권한을 회수(API 103)하면 member.role 은 MEMBER, 강사 정보는 REVOKED 가 된다 → 다시 로그인해도 회수로 본다.
*   저장소는 가짜로 두고 회원·강사 정보의 상태만 바꿔 확인한다
*  */
class InstructorRevokedCheckTest {

    private static final Long MEMBER_ID = 2L;

    private MemberRepository memberRepository;
    private InstructorProfileRepository instructorProfileRepository;
    private MemberService memberService;
    private Member member;
    private InstructorProfile profile;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        instructorProfileRepository = mock(InstructorProfileRepository.class);
        memberService = new MemberService(memberRepository, mock(TermsRepository.class),
                mock(MemberTermsAgreementRepository.class), mock(PasswordEncoder.class), mock(ForbiddenWordService.class),
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                instructorProfileRepository, mock(SocialAccountRepository.class));

        member = mock(Member.class);
        when(member.getMemberId()).thenReturn(MEMBER_ID);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        profile = InstructorProfile.create(MEMBER_ID, "박서연", "예적금·청약");
        profile.approve(1L, LocalDateTime.of(2026, 11, 1, 10, 0));
        when(instructorProfileRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(profile));
    }

    @Test
    @DisplayName("승인된 강사는 회수되지 않은 것으로 본다")
    void approvedInstructorIsNotRevoked() {
        when(member.getRole()).thenReturn(Member.ROLE_INSTRUCTOR);

        assertThat(memberService.isInstructorRevoked(MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("관리자가 회수하면(role MEMBER, 강사 정보 REVOKED) 다시 로그인해도 회수로 본다")
    void revokedByAdminIsRevoked() {
        when(member.getRole()).thenReturn(Member.ROLE_MEMBER);
        profile.revoke();

        assertThat(memberService.isInstructorRevoked(MEMBER_ID)).isTrue();
    }

    @Test
    @DisplayName("role 이 INSTRUCTOR 로 남아 있어도 강사 정보가 회수 상태면 회수로 본다")
    void revokedProfileIsRevokedEvenIfRoleRemains() {
        when(member.getRole()).thenReturn(Member.ROLE_INSTRUCTOR);
        profile.revoke();

        assertThat(memberService.isInstructorRevoked(MEMBER_ID)).isTrue();
    }

    @Test
    @DisplayName("없는 회원은 회수로 본다 (안전한 쪽)")
    void unknownMemberIsRevoked() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(memberService.isInstructorRevoked(99L)).isTrue();
    }
}
