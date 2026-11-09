package com.wanted.moneybeetle.domain.member.model.dto;

import java.util.List;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   약관 목록 응답 (API 1) { "agreements": [ { termsId, title, isRequired, version, content } ] }
*  */
@Getter
public class TermsListDTO {

    private final List<TermsDTO> agreements;

    public TermsListDTO(List<TermsDTO> agreements) {
        this.agreements = agreements;
    }
}
