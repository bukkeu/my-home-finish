package com.wanted.moneybeetle.domain.member.model.entity;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 계정 상태 (SP-06, 이슈 #288)
*   - ACTIVE    : 정상. 로그인할 수 있다
*   - SUSPENDED : 관리자가 정지했다. 로그인할 수 없고 이미 로그인한 세션도 다음 요청에서 끊긴다
*   - WITHDRAWN : 탈퇴했다 (개인정보는 30일 뒤 파기). 다시 바꿀 수 없다
*   DB 컬럼(member.status VARCHAR)에는 name() 그대로 저장되므로 예전 문자열 값과 같다.
*  */
public enum MemberStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN;

    /** 문자열("ACTIVE" 등)로 상태를 찾는다. 모르는 값이면 IllegalArgumentException */
    public static MemberStatus from(String name) {
        return valueOf(name);
    }
}
