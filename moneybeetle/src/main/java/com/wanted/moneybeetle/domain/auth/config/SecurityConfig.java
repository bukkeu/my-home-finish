package com.wanted.moneybeetle.domain.auth.config;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   Spring Security 설정 (세션 방식, 30분 만료 DP-11)
*   - PasswordEncoder(BCrypt) Bean
*   - URL 권한 : /api/v1/admin/** ADMIN, /api/v1/instructor/** INSTRUCTOR, 나머지 /api/v1/** 로그인
*   - 경로는 도메인 단위로 미리 등록해 두고, 새 API 를 만들 때마다 고치지 않는다 (충돌 방지)
*   - formLogin 성공/실패 핸들러 연결, OAuth2 로그인(구글·카카오)
*  */

import com.wanted.moneybeetle.domain.auth.handler.AuthFailHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthLogoutSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration                                 // ① 설정 클래스
@EnableWebSecurity                             // ② Security 설정을 켜는 어노테이션
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();    // ③ BCrypt 암호화 클래스
    }

    @Bean
    public SecurityFilterChain configure(HttpSecurity http,
                                         SecurityErrorHandler securityErrorHandler,
                                         AuthSuccessHandler authSuccessHandler,
                                         AuthFailHandler authFailHandler,
                                         AuthLogoutSuccessHandler authLogoutSuccessHandler,
                                         MemberService memberService) throws Exception {

        // 로그인 한 번(잠금 확인 → 비밀번호 확인 → 성공·실패 기록)을 하나의 트랜잭션으로 묶는 인증 제공자 LoginAuthenticationProvider 는
        // 빈으로 등록돼 있어 Spring Security 가 인증 관리자에 자동으로 넣는다. 여기서 http.authenticationProvider 로 한 번 더 넣으면
        // 같은 제공자가 위·아래 두 인증 관리자에 모두 들어가 로그인 한 번에 실패가 두 번 기록된다 (LoginHttpTest 가 이를 확인한다)

        http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/", "/login", "/signup", "/instructor/login",
                            "/admin/login","/error", "/error/**", "/uploads/**", "/api/v1/auth/**", "/api/v1/agreements", "/api/v1/admin/auth/login").permitAll();      // ④ 누구나
                    auth.requestMatchers("/admin/**", "/api/v1/admin/**").hasAuthority("ADMIN");          // ⑤
                    // ⑥ 승인된 강사만 INSTRUCTOR 권한을 받도록 하는 것은 로그인(AuthService, 김예린 #6)에서 확인
                    auth.requestMatchers("/instructor/**", "/api/v1/instructor/**").hasAuthority("INSTRUCTOR");
                    auth.anyRequest().authenticated();                                         // ⑦ 로그인만 하면
                })
                .formLogin(login -> {
                    login.loginPage("/login");                      // 로그인 화면 GET
                    login.loginProcessingUrl("/api/v1/auth/login");   // 로그인 처리 POST
                    login.usernameParameter("email");
                    login.passwordParameter("password");
                    // 성공·실패 핸들러(#6): JSON 응답, 5회 실패 1분 잠금, 이후 5회마다 5분 잠금(DP-10). 요청은 JSON 이 아니라 폼 형식(email, password)이다
                    login.successHandler(authSuccessHandler);
                    login.failureHandler(authFailHandler);
                    // 로그인 시 세션 ID 재발급(세션 고정 보호)은 Spring Security 기본값이라 따로 설정하지 않는다
                })
                .logout(logout -> {
                    logout.logoutUrl("/api/v1/auth/logout");           // ⑧
                    logout.deleteCookies("JSESSIONID");
                    logout.invalidateHttpSession(true); // ⑨
                    // fetch 요청은 204, 브라우저가 보낸 폼 요청은 "/"로 이동 (API 7)
                    logout.logoutSuccessHandler(authLogoutSuccessHandler);
                })
                // 로그인해 둔 세션도 탈퇴·정지된 뒤에는 막는다 (다른 기기에서 탈퇴해도 이 세션의 다음 요청이 거부된다, SP-06)
                .addFilterBefore(new ActiveMemberFilter(memberService, securityErrorHandler), AuthorizationFilter.class)
                // ⑫ 인증·권한 실패 응답 (API 는 401·403 JSON, 화면은 로그인 화면·403 화면)
                .exceptionHandling(ex -> {
                    ex.authenticationEntryPoint(securityErrorHandler);   // 로그인 안 함
                    ex.accessDeniedHandler(securityErrorHandler);        // 권한 없음
                });

        return http.build();
    }
}
