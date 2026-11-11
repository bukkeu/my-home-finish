package com.wanted.moneybeetle.domain.auth.model.service;

import java.util.regex.Pattern;

/* comment.
*   담당 : DBA ② (회원·인증)
*   로그에 남길 예외의 복사본 (이슈 #299, 점검표 C-3·C-4)
*   - 예외 객체를 그대로 로그에 넘기면 원인(스택 트레이스)은 남지만, 메일 서버·DB 예외의 메시지에는 받는 사람 주소 같은 값이 섞일 수 있다.
*     그래서 원래 예외의 클래스 이름·스택·원인 사슬은 그대로 두고, 메시지 안의 이메일 주소만 '***' 로 가린 복사본을 로그에 넘긴다.
*   - 메시지는 "원래 예외 클래스 이름: 가린 메시지" 로 만든다. 원인(cause)도 같은 방식으로 복사한다 (최대 MAX_DEPTH 단계).
*   - 이메일 주소가 아닌 값(토큰·인증 코드)은 메일 예외 메시지에 들어가지 않는다(본문은 예외 메시지에 실리지 않는다). 새로 그런 값이 실리는 예외를 다루게 되면 여기서 가린다.
*  */
public final class MaskedException extends RuntimeException {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final int MAX_DEPTH = 5;
    static final String MASK = "***";

    private MaskedException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 원래 예외와 같은 스택·원인 사슬을 가진, 메시지의 이메일 주소를 가린 복사본 */
    public static MaskedException of(Throwable original) {
        return copy(original, 0);
    }

    private static MaskedException copy(Throwable original, int depth) {
        Throwable cause = original.getCause();
        MaskedException maskedCause = cause != null && cause != original && depth < MAX_DEPTH ? copy(cause, depth + 1) : null;
        MaskedException masked = new MaskedException(original.getClass().getName() + ": " + mask(original.getMessage()), maskedCause);
        masked.setStackTrace(original.getStackTrace());
        return masked;
    }

    static String mask(String message) {
        return message == null ? "" : EMAIL.matcher(message).replaceAll(MASK);
    }
}
