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
*   가격표 한 칸의 changeRate 입력 검증 (API 128) - -100% 초과, DB DECIMAL(6,2) 범위만 받는다
*  */
class PriceCellDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> invalidFields(String rate) {
        return validator.validate(new PriceCellDTO(1L, 2, null, new BigDecimal(rate))).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void 정상_범위의_변동률은_통과한다() {
        assertThat(invalidFields("-35.00")).isEmpty();
        assertThat(invalidFields("-99.99")).isEmpty();
        assertThat(invalidFields("9999.99")).isEmpty();
    }

    @Test
    void 마이너스_100퍼센트_이하는_거절한다() {
        assertThat(invalidFields("-100")).contains("changeRate");
        assertThat(invalidFields("-150.5")).contains("changeRate");
    }

    @Test
    void 아주_큰_지수_표기와_소수_셋째_자리는_거절한다() {
        assertThat(invalidFields("1E+999999999")).contains("changeRate");
        assertThat(invalidFields("10000")).contains("changeRate");
        assertThat(invalidFields("1.234")).contains("changeRate");
    }
}
