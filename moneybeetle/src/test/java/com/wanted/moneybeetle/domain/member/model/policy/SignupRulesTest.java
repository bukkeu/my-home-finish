package com.wanted.moneybeetle.domain.member.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   가입 규칙 SP-01~05 - 서버·DB 없이 돈다
*  */
class SignupRulesTest {

    @Test
    @DisplayName("SP-01: 이메일은 앞뒤 공백을 지우고 소문자로 바꾼다")
    void normalizeEmail() {
        assertThat(SignupRules.normalizeEmail("  MinJi@Gmail.COM ")).isEqualTo("minji@gmail.com");
        assertThat(SignupRules.normalizeEmail(null)).isNull();
    }

    @Test
    @DisplayName("SP-02: 닉네임은 2~12자의 한글·영문·숫자만 된다")
    void nickname() {
        assertThat(SignupRules.isValidNickname("민지")).isTrue();
        assertThat(SignupRules.isValidNickname("Minji99")).isTrue();
        assertThat(SignupRules.isValidNickname("가나다라마바사아자차카타")).isTrue();      // 12자
        assertThat(SignupRules.isValidNickname("가나다라마바사아자차카타파")).isFalse();    // 13자
        assertThat(SignupRules.isValidNickname("민")).isFalse();                         // 1자
        assertThat(SignupRules.isValidNickname("민 지")).isFalse();                       // 공백
        assertThat(SignupRules.isValidNickname("민지!")).isFalse();                       // 특수문자
        assertThat(SignupRules.isValidNickname(null)).isFalse();
    }

    @Test
    @DisplayName("SP-03: 비밀번호는 8~20자이고 영문과 숫자를 모두 포함해야 한다")
    void password() {
        assertThat(SignupRules.isValidPassword("beetle1234")).isTrue();
        assertThat(SignupRules.isValidPassword("abcdefg1")).isTrue();                    // 8자
        assertThat(SignupRules.isValidPassword("abcdefghij1234567890")).isTrue();        // 20자
        assertThat(SignupRules.isValidPassword("abcdefghij12345678901")).isFalse();      // 21자
        assertThat(SignupRules.isValidPassword("abc123")).isFalse();                     // 6자
        assertThat(SignupRules.isValidPassword("beetlebeetle")).isFalse();               // 숫자 없음
        assertThat(SignupRules.isValidPassword("12345678")).isFalse();                   // 영문 없음
        assertThat(SignupRules.isValidPassword("beetle 1234")).isFalse();                // 공백
    }

    @Test
    @DisplayName("SP-04: 만 14세 이상만 가입 — 생일이 지났는지까지 본다")
    void age() {
        LocalDate today = LocalDate.of(2026, 11, 8);
        assertThat(SignupRules.isAtLeastMinAge(LocalDate.of(2012, 11, 8), today)).isTrue();    // 오늘 만 14세
        assertThat(SignupRules.isAtLeastMinAge(LocalDate.of(2012, 11, 9), today)).isFalse();   // 내일이 14번째 생일
        assertThat(SignupRules.isAtLeastMinAge(LocalDate.of(1999, 4, 12), today)).isTrue();
        assertThat(SignupRules.isAtLeastMinAge(null, today)).isFalse();
    }

    @Test
    @DisplayName("SP-05: 필수 약관에 모두 동의해야 하고 선택 약관은 안 해도 된다")
    void requiredTerms() {
        List<Long> required = List.of(1L, 2L);
        assertThat(SignupRules.agreedAllRequired(required, List.of(1L, 2L))).isTrue();
        assertThat(SignupRules.agreedAllRequired(required, List.of(1L, 2L, 3L))).isTrue();   // 선택 약관 포함
        assertThat(SignupRules.agreedAllRequired(required, List.of(1L, 3L))).isFalse();      // 필수 2번 빠짐
        assertThat(SignupRules.agreedAllRequired(required, null)).isFalse();
        assertThat(SignupRules.agreedAllRequired(List.of(), null)).isTrue();                 // 필수 약관이 없으면 통과
    }
}
