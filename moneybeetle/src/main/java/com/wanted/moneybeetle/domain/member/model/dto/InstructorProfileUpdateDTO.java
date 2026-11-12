package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.InstructorProfileRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   강사 본인 프로필 수정 요청 (이슈 #365, API 12-2) - 이름, 한 줄 소개, 강사 소개, 전문 분야(여러 개)
*   - 닉네임은 여기서 바꾸지 않는다 (API 14)
*   - 입력값만 보고 판단되는 형식(길이·고를 수 있는 분야)은 여기, 금칙어는 Service 에서 검사한다
*   - 요청 JSON 을 객체로 바꾸려면 기본 생성자와 setter 가 필요하다 (수업 DTO 방식, MemberUpdateDTO 와 같음)
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstructorProfileUpdateDTO {

    @NotBlank(message = "이름을 입력해 주세요.")
    private String displayName;

    @Size(max = InstructorProfileRules.HEADLINE_MAX_LENGTH, message = "한 줄 소개는 60자 이하로 입력해 주세요.")
    private String headline;

    @Size(max = InstructorProfileRules.BIO_MAX_LENGTH, message = "강사 소개는 1,000자 이하로 입력해 주세요.")
    private String bio;

    @NotEmpty(message = "전문 분야를 1개 이상 골라 주세요.")
    private List<@NotBlank(message = "전문 분야가 올바르지 않아요.") String> fields;

    /** 앞뒤 공백을 뺀 이름이 2~20자인가 (비어 있는 경우는 위 @NotBlank 가 알린다) */
    @AssertTrue(message = "이름은 2~20자로 입력해 주세요.")
    public boolean isDisplayNameLengthValid() {
        return displayName == null || displayName.isBlank() || InstructorProfileRules.isValidDisplayName(displayName);
    }

    /** 고를 수 있는 전문 분야만 들어 있는가 */
    @AssertTrue(message = "고를 수 없는 전문 분야가 있어요.")
    public boolean isFieldsAllowed() {
        return fields == null || fields.stream().allMatch(InstructorProfileRules::isAllowedField);
    }
}
