package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   약관 한 건 (API 1 agreements)
*  */
@Getter
public class TermsDTO {

    private final Long termsId;
    private final String title;
    /** JSON 필드 이름은 API 명세서의 isRequired 로 맞춘다 */
    @JsonProperty("isRequired")
    private final boolean required;
    private final String version;
    private final String content;

    private TermsDTO(Long termsId, String title, boolean required, String version, String content) {
        this.termsId = termsId;
        this.title = title;
        this.required = required;
        this.version = version;
        this.content = content;
    }

    public static TermsDTO from(Terms terms) {
        return new TermsDTO(terms.getTermsId(), terms.getTitle(), terms.isRequired(), terms.getVersion(), terms.getContent());
    }
}
