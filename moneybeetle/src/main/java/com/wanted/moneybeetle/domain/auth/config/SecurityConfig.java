package com.wanted.moneybeetle.domain.auth.config;

/* comment.
*   담당 : SCM (회원·공통)
*   Spring Security 설정 (세션 방식, 30분 만료 DP-11)
*   - PasswordEncoder(BCrypt) Bean
*   - URL 권한 : /api/v1/admin/** ADMIN, /api/v1/instructor/** INSTRUCTOR, 나머지 /api/v1/** 로그인
*   - 경로는 도메인 단위로 미리 등록해 두고, 새 API 를 만들 때마다 고치지 않는다 (충돌 방지)
*   - formLogin 성공/실패 핸들러 연결, OAuth2 로그인(구글·카카오)
*  */
public class SecurityConfig {

}
