package com.wanted.moneybeetle.domain.term.model.dto;

import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* comment.
*   담당 : DBA ② (경제 용어)
*   경제 용어 등록 요청 (API 96) { term, description } - 입력값만으로 판단되는 규칙(필수, 길이)은 여기서 확인한다.
*  */
public record TermCreateDTO(
        @NotBlank @Size(max = EconomicTerm.TERM_MAX_LENGTH) String term,
        @NotBlank @Size(max = EconomicTerm.DESCRIPTION_MAX_LENGTH) String description) {
}
