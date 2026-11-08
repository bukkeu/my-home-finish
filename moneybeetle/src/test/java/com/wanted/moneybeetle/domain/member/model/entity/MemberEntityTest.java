package com.wanted.moneybeetle.domain.member.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   Member·Terms·MemberTermsAgreement Entity 규칙 - 서버·DB 없이 돈다
*  */
class MemberEntityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Test
    @DisplayName("가입하면 일반 회원·정상 계정·레벨 1·경험치 0·캐시 0으로 시작하고 이메일은 소문자로 저장된다")
    void signupDefaults() {
        Member m = Member.signup("MinJi@Gmail.com", "$2a$10$hash", "민지", LocalDate.of(1999, 4, 12), "FEMALE", TODAY);

        assertThat(m.getEmail()).isEqualTo("minji@gmail.com");
        assertThat(m.getRole()).isEqualTo(Member.ROLE_MEMBER);
        assertThat(m.getStatus()).isEqualTo(Member.STATUS_ACTIVE);
        assertThat(m.isActive()).isTrue();
        assertThat(m.isMember()).isTrue();
        assertThat(m.getLevel()).isEqualTo(1);
        assertThat(m.getExp()).isZero();
        assertThat(m.getCash()).isZero();
        assertThat(m.getAttendanceStreak()).isZero();
    }

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 저장한다 (SignupRules.normalizeEmail 과 같은 규칙)")
    void emailNormalizedLikeRules() {
        Member m = Member.signup("  MinJi@Gmail.COM ", "hash", "민지", LocalDate.of(1999, 4, 12), null, TODAY);
        assertThat(m.getEmail()).isEqualTo("minji@gmail.com");
    }

    @Test
    @DisplayName("만 14세 미만은 가입할 수 없다 (SP-04)")
    void under14() {
        assertThatThrownBy(() -> Member.signup("a@b.com", "hash", "민지", LocalDate.of(2012, 11, 9), null, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호 해시·닉네임·성별 값이 올바르지 않으면 만들 수 없다")
    void invalidInputs() {
        LocalDate birth = LocalDate.of(1999, 4, 12);
        assertThatThrownBy(() -> Member.signup("a@b.com", " ", "민지", birth, null, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.signup("a@b.com", "hash", "", birth, null, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.signup("a@b.com", "hash", "민지", birth, "OTHER", TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.signup("", "hash", "민지", birth, null, TODAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("약관은 이름·본문이 있어야 하고, 동의 기록은 회원·약관이 있어야 한다")
    void termsAndAgreement() {
        Terms t = Terms.create("서비스 이용약관", "본문", true, "1.0", LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(t.isRequired()).isTrue();
        assertThatThrownBy(() -> Terms.create(" ", "본문", true, "1.0", null)).isInstanceOf(IllegalArgumentException.class);

        MemberTermsAgreement a = MemberTermsAgreement.create(1L, 2L, true);
        assertThat(a.isAgreed()).isTrue();
        assertThatThrownBy(() -> MemberTermsAgreement.create(null, 2L, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
