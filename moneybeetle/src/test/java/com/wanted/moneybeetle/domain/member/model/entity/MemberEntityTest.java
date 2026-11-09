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
    @DisplayName("보상을 받으면 캐시·경험치가 늘고, 캐시를 쓰면 줄어든다 (잔액은 0 미만이 될 수 없다)")
    void rewardAndSpend() {
        Member m = Member.signup("minji@gmail.com", "$2a$10$hash", "민지", LocalDate.of(1999, 4, 12), null, TODAY);

        m.addReward(30_000, 100);
        assertThat(m.getCash()).isEqualTo(30_000);
        assertThat(m.getExp()).isEqualTo(100);
        assertThat(m.getLevel()).isEqualTo(1);   // 레벨 재계산은 #11

        m.spendCash(30_000);
        assertThat(m.getCash()).isZero();   // 잔액과 같은 금액은 쓸 수 있다

        assertThatThrownBy(() -> m.spendCash(1)).isInstanceOf(IllegalArgumentException.class);   // 잔액 부족
        assertThatThrownBy(() -> m.spendCash(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.addReward(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.addReward(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(m.getCash()).isZero();   // 거절된 호출은 값을 바꾸지 않는다
    }

    @Test
    @DisplayName("levelUp: 레벨만 변경하고, 같은 레벨·하향·Lv.20 초과는 거부한다")
    void levelUp() {
        Member m = Member.signup("a@b.com", "hash", "민지", LocalDate.of(1999, 4, 12), null, TODAY);

        m.levelUp(4);

        assertThat(m.getLevel()).isEqualTo(4);
        assertThat(m.getCash()).isZero();
        assertThat(m.getExp()).isZero();

        assertThatThrownBy(() -> m.levelUp(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.levelUp(3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.levelUp(21)).isInstanceOf(IllegalArgumentException.class);

        assertThat(m.getLevel()).isEqualTo(4);
        assertThat(m.getCash()).isZero();
        assertThat(m.getExp()).isZero();

        m.levelUp(20);
        assertThat(m.getLevel()).isEqualTo(Member.MAX_LEVEL);
    }

    @Test
    @DisplayName("출석: 처음은 1일, 어제도 출석했으면 +1, 하루라도 빠지면 1일부터 다시, 같은 날 두 번째는 기록하지 않는다 (SP-10~11)")
    void checkAttendance() {
        Member m = Member.signup("a@b.com", "hash", "민지", LocalDate.of(1999, 4, 12), null, TODAY);
        LocalDate day1 = LocalDate.of(2026, 11, 9);

        assertThat(m.checkAttendance(day1)).isTrue();
        assertThat(m.getAttendanceStreak()).isEqualTo(1);
        assertThat(m.hasAttended(day1)).isTrue();

        assertThat(m.checkAttendance(day1)).isFalse();             // 같은 날
        assertThat(m.getAttendanceStreak()).isEqualTo(1);

        assertThat(m.checkAttendance(day1.plusDays(1))).isTrue();  // 이어서
        assertThat(m.checkAttendance(day1.plusDays(2))).isTrue();
        assertThat(m.getAttendanceStreak()).isEqualTo(3);

        assertThat(m.checkAttendance(day1.plusDays(4))).isTrue();  // 하루 빠짐 (3일차 다음 날 건너뜀)
        assertThat(m.getAttendanceStreak()).isEqualTo(1);
        assertThat(m.getLastAttendedDate()).isEqualTo(day1.plusDays(4));
    }

    @Test
    @DisplayName("출석: 이미 기록된 날짜보다 이전·같은 날짜는 값을 바꾸지 않는다 (기기 시간 이상 방어)")
    void checkAttendanceIgnoresPastDates() {
        Member m = Member.signup("a@b.com", "hash", "민지", LocalDate.of(1999, 4, 12), null, TODAY);
        m.checkAttendance(LocalDate.of(2026, 11, 9));
        m.checkAttendance(LocalDate.of(2026, 11, 10));

        assertThat(m.checkAttendance(LocalDate.of(2026, 11, 8))).isFalse();
        assertThat(m.getAttendanceStreak()).isEqualTo(2);
        assertThat(m.getLastAttendedDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThatThrownBy(() -> m.checkAttendance(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("현재 연속 일수: 오늘이나 어제 출석했으면 저장된 값, 그보다 오래됐거나 출석한 적이 없으면 0")
    void currentStreak() {
        Member m = Member.signup("a@b.com", "hash", "민지", LocalDate.of(1999, 4, 12), null, TODAY);
        assertThat(m.currentStreak(LocalDate.of(2026, 11, 9))).isZero();
        assertThat(m.hasAttended(LocalDate.of(2026, 11, 9))).isFalse();

        m.checkAttendance(LocalDate.of(2026, 11, 9));
        m.checkAttendance(LocalDate.of(2026, 11, 10));

        assertThat(m.currentStreak(LocalDate.of(2026, 11, 10))).isEqualTo(2);   // 오늘 출석
        assertThat(m.currentStreak(LocalDate.of(2026, 11, 11))).isEqualTo(2);   // 어제 출석, 오늘은 아직
        assertThat(m.currentStreak(LocalDate.of(2026, 11, 12))).isZero();       // 하루 빠져서 끊김
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
