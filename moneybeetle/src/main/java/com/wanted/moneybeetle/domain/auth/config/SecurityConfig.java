package com.wanted.moneybeetle.domain.auth.config;

/* comment.
*   담당 : SCM (회원·공통)
*   Spring Security 설정 (세션 방식, 30분 만료 DP-11)
*   - PasswordEncoder(BCrypt) Bean
*   - URL 권한 : /api/v1/admin/** ADMIN, /api/v1/instructor/** INSTRUCTOR, 나머지 /api/v1/** 로그인
*   - 경로는 도메인 단위로 미리 등록해 두고, 새 API 를 만들 때마다 고치지 않는다 (충돌 방지)
*   - formLogin 성공/실패 핸들러 연결, OAuth2 로그인(구글·카카오)
*  */

import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration                                 // ① 설정 클래스
@EnableWebSecurity                             // ② Security 설정을 켜는 어노테이션
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();    // ③ BCrypt 암호화 클래스
    }

    @Bean
    public SecurityFilterChain configure(HttpSecurity http,
                                         SecurityErrorHandler securityErrorHandler) throws Exception {

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
                    // TODO(#6 김예린) 성공·실패 핸들러 연결 : JSON 응답, 5회 실패 10분 잠금(DP-10), 출석 기록
                    //   login.successHandler(authSuccessHandler);
                    //   login.failureHandler(authFailHandler);
                    // 로그인 시 세션 ID 재발급(세션 고정 보호)은 Spring Security 기본값이라 따로 설정하지 않는다
                })
                .logout(logout -> {
                    logout.logoutUrl("/api/v1/auth/logout");           // ⑧
                    logout.deleteCookies("JSESSIONID");
                    logout.invalidateHttpSession(true); // ⑨
                    logout.logoutSuccessUrl("/");
                })
                // ⑫ 인증·권한 실패 응답 (API 는 401·403 JSON, 화면은 로그인 화면·403 화면)
                .exceptionHandling(ex -> {
                    ex.authenticationEntryPoint(securityErrorHandler);   // 로그인 안 함
                    ex.accessDeniedHandler(securityErrorHandler);        // 권한 없음
                });

        return http.build();
    }
}
