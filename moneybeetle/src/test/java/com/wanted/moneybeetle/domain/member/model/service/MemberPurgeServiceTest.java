package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class MemberPurgeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 3, 0);

    private MemberRepository memberRepository;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        memberService = new MemberService(memberRepository, mock(TermsRepository.class),
                mock(MemberTermsAgreementRepository.class), mock(PasswordEncoder.class), mock(ForbiddenWordService.class),
                Clock.fixed(Instant.parse("2026-11-08T18:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(InstructorProfileRepository.class), mock(com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository.class));
    }

    private Member withdrawn(long id) {
        Member member = Member.signup("m" + id + "@gmail.com", "hash", "회원" + id, LocalDate.of(1999, 4, 12), "MALE",
                LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(member, "memberId", id);
        member.withdraw(LocalDateTime.of(2026, 10, 1, 9, 0));
        return member;
    }

    @Test
    @DisplayName("탈퇴 후 30일 전 시각을 기준으로 대상을 찾아 개인정보를 지우고 처리한 수를 돌려준다")
    void purgesMembersDueBeforeThirtyDaysAgo() {
        Member a = withdrawn(8);
        Member b = withdrawn(9);
        when(memberRepository.findPurgeTargets(any(), any(Pageable.class))).thenReturn(List.of(a, b));

        int purged = memberService.purgeWithdrawnMembers(NOW, 100);

        assertThat(purged).isEqualTo(2);
        assertThat(a.getEmail()).isNull();
        assertThat(a.getNickname()).isEqualTo("탈퇴한 사용자#8");
        assertThat(b.getBirthDate()).isNull();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberRepository).findPurgeTargets(cutoff.capture(), eq(PageRequest.of(0, 100)));
        assertThat(cutoff.getValue()).isEqualTo(NOW.minusDays(30));
    }

    @Test
    @DisplayName("지울 회원이 없으면 0 을 돌려준다")
    void nothingToPurge() {
        when(memberRepository.findPurgeTargets(any(), any(Pageable.class))).thenReturn(List.of());

        assertThat(memberService.purgeWithdrawnMembers(NOW, 100)).isZero();
    }
}
