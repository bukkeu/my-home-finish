package com.wanted.moneybeetle.domain.member.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberPurgeTest {

    private Member withdrawnMember() {
        Member member = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 10, 1));
        ReflectionTestUtils.setField(member, "memberId", 8L);
        member.changeProfileImageUrl("/uploads/minji.png");
        member.withdraw(LocalDateTime.of(2026, 10, 1, 9, 0));
        return member;
    }

    @Test
    @DisplayName("개인정보를 지우면 이메일·비밀번호·생년월일·성별·프로필 이미지는 NULL, 닉네임은 '탈퇴한 사용자#ID' 가 된다")
    void purgesPersonalInfo() {
        Member member = withdrawnMember();

        member.purgePersonalInfo();

        assertThat(member.getEmail()).isNull();
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getBirthDate()).isNull();
        assertThat(member.getGender()).isNull();
        assertThat(member.getProfileImageUrl()).isNull();
        assertThat(member.getNickname()).isEqualTo("탈퇴한 사용자#8");
    }

    @Test
    @DisplayName("행은 남기고 학습·거래 기록에 필요한 값(번호·상태·탈퇴 시각·역할)은 그대로 둔다")
    void keepsRecordFields() {
        Member member = withdrawnMember();

        member.purgePersonalInfo();

        assertThat(member.getMemberId()).isEqualTo(8L);
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
        assertThat(member.getWithdrawnAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(member.getRole()).isEqualTo(Member.ROLE_MEMBER);
    }

    @Test
    @DisplayName("탈퇴하지 않은 회원의 개인정보는 지울 수 없다")
    void onlyWithdrawnMembers() {
        Member active = Member.signup("minji@gmail.com", "hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE",
                LocalDate.of(2026, 10, 1));

        assertThatThrownBy(active::purgePersonalInfo).isInstanceOf(IllegalArgumentException.class);
        assertThat(active.getEmail()).isEqualTo("minji@gmail.com");
    }

    @Test
    @DisplayName("이미 지운 회원에 다시 불러도 같은 결과다")
    void purgeIsIdempotent() {
        Member member = withdrawnMember();
        member.purgePersonalInfo();

        member.purgePersonalInfo();

        assertThat(member.getNickname()).isEqualTo("탈퇴한 사용자#8");
        assertThat(member.getEmail()).isNull();
    }
}
