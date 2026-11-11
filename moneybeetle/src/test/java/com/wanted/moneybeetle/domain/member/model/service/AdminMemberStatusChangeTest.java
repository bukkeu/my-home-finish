package com.wanted.moneybeetle.domain.member.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.member.exception.AdminMemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.AdminMemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.entity.MemberStatus;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/* comment.
*   담당 : DBA ① 신아림 (#295)
*   관리자 계정 정지·해제(API 102): 상태 판단은 Member.changeStatus 에 맡기고, 서비스는 조회 → 변경 → 바뀌었으면 관리자 로그
*  */
class AdminMemberStatusChangeTest {

    private static final Long ADMIN = 1L;
    private static final Long MEMBER = 8L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private MemberRepository memberRepository;
    private AdminLogService adminLogService;
    private AdminMemberService adminMemberService;
    private Member member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        adminLogService = mock(AdminLogService.class);
        adminMemberService = new AdminMemberService(mock(AdminMemberRepository.class), memberRepository,
                mock(InstructorProfileRepository.class), adminLogService, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")));
        member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE", TODAY);
        when(memberRepository.findByIdForUpdate(MEMBER)).thenReturn(Optional.of(member));
    }

    private String loggedDetail(String action) {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(adminLogService).record(eq(ADMIN), eq(action), eq("MEMBER"), eq(MEMBER), detail.capture());
        return detail.getValue();
    }

    @Test
    @DisplayName("정지하면 SUSPENDED 가 되고, 앞뒤 공백을 뺀 사유와 전후 상태를 관리자 로그에 남긴다")
    void suspend() {
        adminMemberService.changeStatus(ADMIN, MEMBER, "SUSPENDED", "  욕설 반복 ");

        assertThat(member.getMemberStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedReason()).isEqualTo("욕설 반복");
        assertThat(loggedDetail("MEMBER_SUSPEND"))
                .contains("\"before\":\"ACTIVE\"", "\"after\":\"SUSPENDED\"", "\"reason\":\"욕설 반복\"");
    }

    @Test
    @DisplayName("해제하면 ACTIVE 가 되고 MEMBER_ACTIVATE 로그를 남긴다 (사유는 없다)")
    void activate() {
        member.suspend("욕설 반복");

        adminMemberService.changeStatus(ADMIN, MEMBER, "ACTIVE", null);

        assertThat(member.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(loggedDetail("MEMBER_ACTIVATE")).contains("\"after\":\"ACTIVE\"").doesNotContain("reason");
    }

    @Test
    @DisplayName("지금과 같은 상태로 바꾸면 아무것도 바꾸지 않고 로그도 남기지 않는다")
    void sameStatusDoesNothing() {
        adminMemberService.changeStatus(ADMIN, MEMBER, "ACTIVE", null);

        verify(adminLogService, never()).record(anyLong(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("정지 사유가 비었으면 회원을 조회하기 전에 REASON_REQUIRED")
    void suspendNeedsReason() {
        assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN, MEMBER, "SUSPENDED", "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AdminMemberErrorCode.REASON_REQUIRED);
        verify(memberRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("WITHDRAWN·모르는 값·null 은 INVALID_INPUT (탈퇴는 이 API 로 만들 수 없다)")
    void invalidTarget() {
        for (String status : new String[] {"WITHDRAWN", "suspended", "", null}) {
            assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN, MEMBER, status, "사유"))
                    .as("status=%s", status)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_INPUT);
        }
        verify(memberRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("탈퇴한 회원은 MEMBER_NOT_CHANGEABLE")
    void withdrawnMember() {
        member.withdraw(LocalDateTime.of(2026, 11, 12, 10, 0));

        assertThatThrownBy(() -> adminMemberService.changeStatus(ADMIN, MEMBER, "SUSPENDED", "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AdminMemberErrorCode.MEMBER_NOT_CHANGEABLE);
    }
}
