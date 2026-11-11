package com.wanted.moneybeetle.domain.trading.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (모의투자)
*   시즌 설정 요청의 입력 검증 - Controller 의 @Valid 와 같은 검사를 서버 없이 확인한다
*   - 이월 비율은 DB decimal(5,2) 에 그대로 들어가는 값(0~100, 소수 둘째 자리까지)만 받는다
*  */
class SeasonSettingDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> invalidFields(SeasonSettingDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void 소수_둘째_자리까지의_이월_비율은_통과한다() {
        assertThat(invalidFields(new SeasonSettingDTO(new BigDecimal("33.33"), null))).isEmpty();
    }

    @Test
    void 소수_셋째_자리가_있는_이월_비율은_거절한다() {
        assertThat(invalidFields(new SeasonSettingDTO(new BigDecimal("33.335"), null)))
                .containsExactly("carryoverRate");
    }

    @Test
    void 이월_비율이_빠지면_거절한다() {
        assertThat(invalidFields(new SeasonSettingDTO(null, null))).containsExactly("carryoverRate");
    }
}
