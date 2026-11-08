package com.wanted.moneybeetle.domain.member.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 요청의 입력 검증(입력 정책) - Controller 의 @Valid 와 같은 검사를 서버 없이 확인한다
*  */
class SignupDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private SignupDTO valid() {
        return new SignupDTO("minji@gmail.com", "beetle1234", "beetle1234", "민지",
                LocalDate.of(1999, 4, 12), "FEMALE", List.of(1L, 2L, 3L));
    }

    private Set<String> messages(SignupDTO dto) {
        return validator.validate(dto).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("올바른 요청은 검증을 통과한다")
    void valid_passes() {
        assertThat(messages(valid())).isEmpty();
    }

    @Test
    @DisplayName("성별은 비워도 된다(필수 여부 미정)")
    void genderOptional() {
        SignupDTO dto = valid();
        dto.setGender(null);
        assertThat(messages(dto)).isEmpty();
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 '비밀번호가 서로 달라요.'")
    void passwordMismatch() {
        SignupDTO dto = valid();
        dto.setPasswordConfirm("different1234");
        assertThat(messages(dto)).containsExactly("비밀번호가 서로 달라요.");
    }

    @Test
    @DisplayName("이메일 형식·닉네임·비밀번호 규칙을 어기면 사용자에게 보여 줄 문구가 나온다")
    void formatViolations() {
        SignupDTO dto = valid();
        dto.setEmail("not-an-email");
        dto.setNickname("민");
        dto.setPassword("short1");
        dto.setPasswordConfirm("short1");
        assertThat(messages(dto)).contains(
                "이메일 형식이 올바르지 않아요.",
                "닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요.",
                "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요.");
    }

    @Test
    @DisplayName("빈 약관 목록·잘못된 성별은 거절한다 (생년월일이 과거인지는 서울 날짜가 필요해 SignupRules 가 본다)")
    void otherViolations() {
        SignupDTO dto = valid();
        dto.setAgreedTermsIds(List.of());
        dto.setGender("OTHER");
        assertThat(messages(dto)).contains("약관 동의 정보가 필요해요.", "성별은 MALE 또는 FEMALE 이어야 해요.");
    }

    @Test
    @DisplayName("약관 ID 목록에 null 이 섞이면 거절한다")
    void nullTermsElement() {
        SignupDTO dto = valid();
        dto.setAgreedTermsIds(Arrays.asList(1L, null));
        assertThat(messages(dto)).contains("약관 동의 정보가 올바르지 않아요.");
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 안내 문구가 나온다")
    void requiredFields() {
        SignupDTO dto = new SignupDTO();
        assertThat(messages(dto)).contains("이메일을 입력해 주세요.", "비밀번호를 입력해 주세요.", "닉네임을 입력해 주세요.", "생년월일을 입력해 주세요.");
    }
}
