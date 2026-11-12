package com.wanted.moneybeetle.domain.member.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   InstructorProfile.changeProfile (이슈 #365, API 12-2) - 서버·DB 없이 돈다
*  */
class InstructorProfileChangeTest {

    private InstructorProfile approved() {
        InstructorProfile profile = InstructorProfile.create(7L, "박서연", "청약");
        profile.approve(1L, LocalDateTime.of(2026, 11, 10, 9, 0));
        return profile;
    }

    @Test
    @DisplayName("새 강사 정보는 한 줄 소개·강사 소개가 비어 있다")
    void newProfileHasNoHeadlineOrBio() {
        InstructorProfile profile = InstructorProfile.create(7L, "박서연", "청약");

        assertThat(profile.getHeadline()).isNull();
        assertThat(profile.getBio()).isNull();
    }

    @Test
    @DisplayName("이름·한 줄 소개·강사 소개·전문 분야를 한꺼번에 바꾸고 승인 상태는 그대로 둔다")
    void changesProfile() {
        InstructorProfile profile = approved();

        profile.changeProfile("박서연 강사", "청약 가점, 한 번에", "10년차 공인중개사입니다.", "청약,부동산");

        assertThat(profile.getDisplayName()).isEqualTo("박서연 강사");
        assertThat(profile.getHeadline()).isEqualTo("청약 가점, 한 번에");
        assertThat(profile.getBio()).isEqualTo("10년차 공인중개사입니다.");
        assertThat(profile.getField()).isEqualTo("청약,부동산");
        assertThat(profile.isApproved()).isTrue();
        assertThat(profile.getApprovedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("한 줄 소개·강사 소개는 null 로 바꿔 지울 수 있다")
    void headlineAndBioCanBeCleared() {
        InstructorProfile profile = approved();
        profile.changeProfile("박서연", "소개", "긴 소개", "청약");

        profile.changeProfile("박서연", null, null, "청약");

        assertThat(profile.getHeadline()).isNull();
        assertThat(profile.getBio()).isNull();
    }

    @Test
    @DisplayName("이름·전문 분야가 비어 있거나 컬럼 길이를 넘으면 IllegalArgumentException 이고 값은 바뀌지 않는다")
    void rejectsInvalidValues() {
        InstructorProfile profile = approved();

        assertThatThrownBy(() -> profile.changeProfile(null, null, null, "청약")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile(" ", null, null, "청약")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile("박서연", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile("박서연", null, null, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile("가".repeat(31), null, null, "청약")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile("박서연", "가".repeat(61), null, "청약")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile("박서연", null, "가".repeat(1001), "청약")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.changeProfile("박서연", null, null, "가".repeat(101))).isInstanceOf(IllegalArgumentException.class);

        assertThat(profile.getDisplayName()).isEqualTo("박서연");
        assertThat(profile.getField()).isEqualTo("청약");
    }

    @Test
    @DisplayName("길이 한도 딱 그 길이는 저장된다")
    void acceptsBoundaryLengths() {
        InstructorProfile profile = approved();

        profile.changeProfile("가".repeat(30), "가".repeat(60), "가".repeat(1000), "가".repeat(100));

        assertThat(profile.getBio()).hasSize(1000);
    }
}
