package com.wanted.moneybeetle.domain.member.model.policy;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/* comment.
*   담당 : DBA ② (회원·인증)
*   가입 규칙 (정책 정의서 SP-01~05) - 값만 보고 판단되는 순수한 검사들이다. 서버·DB 없이 테스트한다.
*   DB 조회가 필요한 규칙(이메일·닉네임 중복, 금칙어)은 MemberService 가 이 규칙들과 함께 순서대로 부른다.
*   위반하면 BusinessException 을 던지는 일은 Service 가 한다 (#2 의 BusinessException 이 올라온 뒤, #5 2차).
*  */
public final class SignupRules {

    /** SP-02: 2~12자, 한글·영문·숫자만 */
    public static final String NICKNAME_PATTERN = "^[가-힣a-zA-Z0-9]{2,12}$";

    /** SP-03: 8~20자, 영문과 숫자를 모두 포함 */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,20}$";

    /** SP-04: 가입 연령 */
    public static final int MIN_AGE = 14;

    private SignupRules() {
    }

    /** SP-01: 대소문자를 구분하지 않으므로 앞뒤 공백을 지우고 소문자로 바꿔 저장·조회한다 */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidNickname(String nickname) {
        return nickname != null && nickname.matches(NICKNAME_PATTERN);
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.matches(PASSWORD_PATTERN);
    }

    /** SP-04: 만 14세 이상인가 (생일이 지났는지까지 본다) */
    public static boolean isAtLeastMinAge(LocalDate birthDate, LocalDate today) {
        return birthDate != null && !birthDate.plusYears(MIN_AGE).isAfter(today);
    }

    /**
     * SP-05: 필수 약관에 모두 동의했는가. 선택 약관은 동의하지 않아도 된다.
     *
     * @param requiredTermsIds 필수 약관 ID 전체
     * @param agreedTermsIds   회원이 동의한 약관 ID (null 이면 동의 없음)
     */
    public static boolean agreedAllRequired(Collection<Long> requiredTermsIds, Collection<Long> agreedTermsIds) {
        Collection<Long> agreed = agreedTermsIds == null ? List.<Long>of() : agreedTermsIds;
        return Set.copyOf(agreed).containsAll(requiredTermsIds);
    }
}
