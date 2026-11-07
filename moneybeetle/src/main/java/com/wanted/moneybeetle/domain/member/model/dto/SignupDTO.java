package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 요청 (API 4) - 입력값만 보고 판단되는 규칙(입력 정책)은 여기 어노테이션으로 선언한다.
*   Controller 파라미터에 @Valid 가 있어야 검사한다. 요청 JSON 을 객체로 바꾸려면 기본 생성자와 setter 가 필요하다 (수업 DTO 방식).
*   - 이메일 형식 SP-01, 닉네임 2~12자 한글·영문·숫자 SP-02, 비밀번호 8~20자 영문+숫자 SP-03, 비밀번호 확인 SP-03
*   - 중복·금칙어·만 14세·필수 약관은 DB 조회나 오늘 날짜가 필요한 규칙이라 Service 쪽 정책에서 검사한다.
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignupDTO {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않아요.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Pattern(regexp = SignupRules.PASSWORD_PATTERN, message = "비밀번호는 8~20자의 영문과 숫자를 모두 포함해야 해요.")
    private String password;

    @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.")
    private String passwordConfirm;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Pattern(regexp = SignupRules.NICKNAME_PATTERN, message = "닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요.")
    private String nickname;

    @NotNull(message = "생년월일을 입력해 주세요.")
    @Past(message = "생년월일이 올바르지 않아요.")
    private LocalDate birthDate;

    /** 성별은 필수 여부가 미정이라 비워도 된다. 값이 있으면 MALE / FEMALE */
    @Pattern(regexp = "MALE|FEMALE", message = "성별은 MALE 또는 FEMALE 이어야 해요.")
    private String gender;

    @NotEmpty(message = "약관 동의 정보가 필요해요.")
    private List<Long> agreedTermsIds;

    /** 비밀번호와 비밀번호 확인이 같은가 (SP-03). 검증 실패 시 위 message 로 나간다 */
    @AssertTrue(message = "비밀번호가 서로 달라요.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
