package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ① 신아림 (#30, API 12 강사 가입 신청) - 회원 파일 담당 DBA ② 김예린 확인
*   강사 가입 신청 요청 (화면설계 5.1.4: 이메일, 비밀번호, 강사명, 전문 분야, 약관 동의)
*   - 강사명(displayName)을 회원 닉네임으로도 쓴다 (팀 결정, #30). 그래서 닉네임과 같은 규칙: 2~12자 한글·영문·숫자 (SP-02)
*     중복·금칙어는 DB 조회가 필요해서 Service 에서 검사한다 (NICKNAME_DUPLICATED, FORBIDDEN_WORD)
*   - 학습자 가입(SignupDTO)과 달리 생년월일·성별은 받지 않는다 (강사 신청 화면에 없음, member.birth_date 는 비워 둘 수 있음)
*   - 요청 JSON 을 객체로 바꾸려면 기본 생성자와 setter 가 필요하다 (수업 DTO 방식, SignupDTO 와 같음)
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstructorSignupDTO {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않아요.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Pattern(regexp = SignupRules.PASSWORD_PATTERN, message = "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요.")
    private String password;

    @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.")
    private String passwordConfirm;

    @NotBlank(message = "강사명을 입력해 주세요.")
    @Pattern(regexp = SignupRules.NICKNAME_PATTERN, message = "강사명은 2~12자의 한글·영문·숫자만 쓸 수 있어요.")
    private String displayName;

    @NotBlank(message = "전문 분야를 입력해 주세요.")
    @Size(max = 100, message = "전문 분야는 100자 이하로 입력해 주세요.")
    private String field;

    @NotEmpty(message = "약관 동의 정보가 필요해요.")
    private List<@NotNull(message = "약관 동의 정보가 올바르지 않아요.") Long> agreedTermsIds;

    /** 비밀번호와 비밀번호 확인이 같은가 (SP-03) */
    @AssertTrue(message = "비밀번호가 서로 달라요.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
