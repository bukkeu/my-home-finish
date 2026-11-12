package com.wanted.moneybeetle.domain.member.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   InstructorProfileRules (이슈 #365) - 값만 보고 판단되는 강사 프로필 규칙. 서버·DB 없이 돈다
*  */
class InstructorProfileRulesTest {

    @Test
    @DisplayName("이름은 앞뒤 공백을 뺀 2~20자만 맞다")
    void displayNameLength() {
        assertThat(InstructorProfileRules.isValidDisplayName("박서")).isTrue();
        assertThat(InstructorProfileRules.isValidDisplayName("가".repeat(20))).isTrue();
        assertThat(InstructorProfileRules.isValidDisplayName("  박서연  ")).isTrue();

        assertThat(InstructorProfileRules.isValidDisplayName("박")).isFalse();
        assertThat(InstructorProfileRules.isValidDisplayName("  박  ")).isFalse();     // 공백을 빼면 1자
        assertThat(InstructorProfileRules.isValidDisplayName("가".repeat(21))).isFalse();
        assertThat(InstructorProfileRules.isValidDisplayName(null)).isFalse();
    }

    @Test
    @DisplayName("고를 수 있는 전문 분야는 7개뿐이다")
    void allowedFields() {
        assertThat(InstructorProfileRules.FIELDS)
                .containsExactly("청약", "ETF", "주식", "부동산", "예적금", "재테크 기초", "대출");
        assertThat(InstructorProfileRules.isAllowedField("재테크 기초")).isTrue();
        assertThat(InstructorProfileRules.isAllowedField(" 청약 ")).isTrue();

        assertThat(InstructorProfileRules.isAllowedField("암호화폐")).isFalse();
        assertThat(InstructorProfileRules.isAllowedField("etf")).isFalse();             // 대소문자도 정해진 값 그대로
        assertThat(InstructorProfileRules.isAllowedField("")).isFalse();
        assertThat(InstructorProfileRules.isAllowedField(null)).isFalse();
    }

    @Test
    @DisplayName("전문 분야는 쉼표로 이어 저장하고, 공백을 빼고 중복은 첫 번째만 남기며 순서를 지킨다")
    void joinFields() {
        assertThat(InstructorProfileRules.joinFields(List.of("청약", "부동산"))).isEqualTo("청약,부동산");
        assertThat(InstructorProfileRules.joinFields(List.of(" 주식 ", "청약", "주식"))).isEqualTo("주식,청약");
        assertThat(InstructorProfileRules.joinFields(List.of("재테크 기초"))).isEqualTo("재테크 기초");
    }

    @Test
    @DisplayName("저장된 문자열을 목록으로 돌린다. 쉼표 뒤 공백·빈 값·옛 값(목록에 없는 값)도 견딘다")
    void splitFields() {
        assertThat(InstructorProfileRules.splitFields("청약,부동산")).containsExactly("청약", "부동산");
        assertThat(InstructorProfileRules.splitFields("청약, ETF")).containsExactly("청약", "ETF");   // 가입 때 직접 쓴 값
        assertThat(InstructorProfileRules.splitFields("청약,,대출, ")).containsExactly("청약", "대출");
        assertThat(InstructorProfileRules.splitFields("부동산 투자")).containsExactly("부동산 투자");   // 목록에 없는 옛 값도 그대로
        assertThat(InstructorProfileRules.splitFields("")).isEmpty();
        assertThat(InstructorProfileRules.splitFields("   ")).isEmpty();
        assertThat(InstructorProfileRules.splitFields(null)).isEmpty();
    }

    @Test
    @DisplayName("고를 수 있는 분야 7개를 모두 이어도 컬럼 길이(100자) 안에 들어간다")
    void allFieldsFitColumn() {
        assertThat(InstructorProfileRules.joinFields(InstructorProfileRules.FIELDS).length()).isLessThanOrEqualTo(100);
    }
}
