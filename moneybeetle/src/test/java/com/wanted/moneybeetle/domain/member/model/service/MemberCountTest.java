package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberCountTest {

    @Test
    @DisplayName("관리자 대시보드용 회원 수: 학습자(MEMBER) 전체와 그중 탈퇴한 수만 센다 (강사·관리자 제외)")
    void countsLearnersAndWithdrawn() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        when(memberRepository.countByRole(Member.ROLE_MEMBER)).thenReturn(5L);
        when(memberRepository.countByRoleAndStatus(Member.ROLE_MEMBER, Member.STATUS_WITHDRAWN)).thenReturn(1L);
        MemberService memberService = new MemberService(memberRepository, mock(TermsRepository.class),
                mock(MemberTermsAgreementRepository.class), mock(PasswordEncoder.class), mock(ForbiddenWordService.class),
                Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(InstructorProfileRepository.class),
                mock(com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository.class));

        var count = memberService.countMembers();

        assertThat(count.total()).isEqualTo(5);
        assertThat(count.withdrawn()).isEqualTo(1);
    }
}
