package com.wanted.moneybeetle.domain.auth.model.dto;

import java.io.Serializable;
import java.time.Instant;

/* comment.
*   담당 : DBA ② (회원·인증)
*   가입 중인 사람의 본인인증 진행 상태 (이슈 #9, API 5·5-1). HTTP 세션에 담아 둔다.
*   - 코드를 보낸 뒤: 이메일, 코드 해시(원문은 담지 않는다), 코드 만료 시각, 틀린 횟수
*   - 인증한 뒤: 코드 정보는 지우고 "이 이메일은 verifiedUntil 까지 인증됨"만 남는다. 가입(API 4)이 이 표시를 확인한다.
*  */
public class IdentityVerificationState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String email;
    // verify 는 이 객체로 잠가 값을 바꾸지만, 가입 전 확인(requireVerified)은 잠그지 않고 읽는다. 바뀐 값이 바로 보이도록 volatile 로 둔다
    private volatile String codeHash;
    private volatile Instant codeExpiresAt;
    private volatile int failures;
    private volatile Instant verifiedUntil;

    private IdentityVerificationState(String email, String codeHash, Instant codeExpiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
    }

    /** 코드를 보낸 직후의 상태. email 은 정규화(소문자·공백 제거)한 값이다 */
    public static IdentityVerificationState issued(String email, String codeHash, Instant codeExpiresAt) {
        return new IdentityVerificationState(email, codeHash, codeExpiresAt);
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getFailures() {
        return failures;
    }

    /** 코드를 아직 쓸 수 있는가 (인증 전이고 만료 전) */
    public boolean isCodeActive(Instant now) {
        return codeHash != null && codeExpiresAt != null && now.isBefore(codeExpiresAt);
    }

    /** 이 이메일이 인증된 상태이고 아직 유효한가 */
    public boolean isVerifiedFor(String normalizedEmail, Instant now) {
        return verifiedUntil != null && now.isBefore(verifiedUntil) && email.equals(normalizedEmail);
    }

    /** 틀린 코드를 한 번 더 센다. 세어진 횟수를 돌려준다. 호출하는 쪽이 이 객체로 잠근 상태에서 부른다 (IdentityVerificationService.verify) */
    public int recordFailure() {
        return ++failures;
    }

    /** 인증을 마쳤다. 코드 정보는 지우고 verifiedUntil 까지 가입에 쓸 수 있게 한다 */
    public void completeVerification(Instant verifiedUntil) {
        this.verifiedUntil = verifiedUntil;
        this.codeHash = null;
        this.codeExpiresAt = null;
        this.failures = 0;
    }
}
