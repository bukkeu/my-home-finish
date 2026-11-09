package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   설정 수정 요청 (API 14) - 닉네임, 생년월일, 성별. 이메일은 바꿀 수 없어서 필드가 없다 (USR-12).
*   값이 없는(null) 항목은 바꾸지 않는다. 값이 있으면 가입 때와 같은 규칙으로 검사한다.
*   - 입력값만 보고 판단되는 형식은 여기, 중복·금칙어·생년월일(과거·만 14세)은 Service 에서 검사한다.
*  */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberUpdateDTO {

    @Pattern(regexp = SignupRules.NICKNAME_PATTERN, message = "닉네임은 2~12자의 한글·영문·숫자만 쓸 수 있어요.")
    private String nickname;

    private LocalDate birthDate;

    @Pattern(regexp = "MALE|FEMALE", message = "성별은 MALE 또는 FEMALE 이어야 해요.")
    private String gender;
}
