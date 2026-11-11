package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   간편 로그인으로 처음 온 사람이 이어서 입력하는 가입 정보 (API 8-2, DP-13, 이슈 #9)
*   - 이메일은 요청에 없다: 구글·카카오가 확인해 준 이메일을 세션에서 쓴다 (조작할 수 없게).
*   - 비밀번호도 없다 (간편 로그인으로만 쓰는 계정). 닉네임은 제공자가 준 이름을 화면이 미리 채워 줄 뿐 사용자가 정한다.
*   - 입력 형식 규칙은 학습자 가입(SignupDTO)과 같다. 중복·금칙어·생년월일·약관은 Service 가 검사한다.
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SocialSignupDTO {

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Pattern(regexp = SignupRules.NICKNAME_PATTERN, message = "닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요.")
    private String nickname;

    @NotNull(message = "생년월일을 입력해 주세요.")
    private LocalDate birthDate;

    /** 성별은 필수 여부가 미정이라 비워도 된다. 값이 있으면 MALE / FEMALE */
    @Pattern(regexp = "MALE|FEMALE", message = "성별은 MALE 또는 FEMALE 이어야 해요.")
    private String gender;

    @NotEmpty(message = "약관 동의 정보가 필요해요.")
    private List<@NotNull(message = "약관 동의 정보가 올바르지 않아요.") Long> agreedTermsIds;
}
