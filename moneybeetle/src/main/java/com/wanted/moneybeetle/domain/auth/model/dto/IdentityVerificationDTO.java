package com.wanted.moneybeetle.domain.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* comment.
*   담당 : DBA ② (회원·인증)
*   본인인증 요청·응답 (이슈 #9, API 5·5-1). 요청 JSON 을 객체로 바꾸려면 기본 생성자와 setter 가 필요하다 (수업 DTO 방식).
*  */
public class IdentityVerificationDTO {

    private IdentityVerificationDTO() {
    }

    /** API 5-1: 인증 코드 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeRequest {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않아요.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
        private String email;
    }

    /** API 5: 받은 코드로 인증 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Verify {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않아요.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
        private String email;

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(regexp = "\\d{6}", message = "인증번호는 숫자 6자리예요.")
        private String code;
    }

    /** API 5 응답 { "verified": true } */
    @Getter
    public static class Result {

        private final boolean verified;

        private Result(boolean verified) {
            this.verified = verified;
        }

        public static Result of(boolean verified) {
            return new Result(verified);
        }
    }
}
