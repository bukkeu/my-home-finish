package com.wanted.moneybeetle.domain.auth.config;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   Spring Security 설정 (세션 방식, 30분 만료 DP-11)
*   - PasswordEncoder(BCrypt) Bean
*   - URL 권한 : /api/v1/admin/** ADMIN, /api/v1/instructor/** INSTRUCTOR, 나머지 /api/v1/** 로그인
*   - 경로는 도메인 단위로 미리 등록해 두고, 새 API 를 만들 때마다 고치지 않는다 (충돌 방지)
*   - formLogin 성공/실패 핸들러 연결, OAuth2 로그인(구글)
*   - 로그인 상태 유지(Remember Me, #367) : 로그인 요청에 remember-me=true 를 보내면 14일 동안 쿠키로 다시 로그인된다
*  */

import com.wanted.moneybeetle.domain.auth.handler.AdminAuthSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthFailHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthLogoutSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.domain.auth.handler.OAuth2FailHandler;
import com.wanted.moneybeetle.domain.auth.handler.OAuth2SuccessHandler;
import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.auth.model.dto.AdminLoginDetails;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

@Configuration                                 // ① 설정 클래스
@EnableWebSecurity                             // ② Security 설정을 켜는 어노테이션
public class SecurityConfig {

    /** 로그인 상태 유지 : 로그인 요청 값 이름과 쿠키 이름 (#367). 화면 체크박스의 name 과 같아야 한다 */
    static final String REMEMBER_ME = "remember-me";
    /** 로그인 상태 유지 기간 14일 (#367) */
    static final int REMEMBER_ME_SECONDS = (int) Duration.ofDays(14).toSeconds();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();    // ③ BCrypt 암호화 클래스
    }

    /**
     * 관리자 로그인 (API 83, 이슈 #33). 일반 로그인과 같은 인증 제공자(잠금·실패 기록 공유)를 쓰고, 이 주소로 온 시도에는
     * AdminLoginDetails 표시를 붙여 ADMIN 권한이 아니면 인증 제공자가 막게 한다. 이 주소만 따로 처리하려고 필터 체인을 하나 더 둔다.
     * 요청은 일반 로그인처럼 폼 형식(email, password)이고 CSRF 토큰이 필요하다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminLoginChain(HttpSecurity http,
                                               SecurityErrorHandler securityErrorHandler,
                                               AdminAuthSuccessHandler adminAuthSuccessHandler,
                                               AuthFailHandler authFailHandler) throws Exception {
        http.securityMatcher("/api/v1/admin/auth/login")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(login -> {
                    login.loginPage("/admin/login");
                    login.loginProcessingUrl("/api/v1/admin/auth/login");
                    login.usernameParameter("email");
                    login.passwordParameter("password");
                    login.authenticationDetailsSource(AdminLoginDetails::new);
                    login.successHandler(adminAuthSuccessHandler);
                    login.failureHandler(authFailHandler);
                })
                .exceptionHandling(ex -> {
                    ex.authenticationEntryPoint(securityErrorHandler);
                    ex.accessDeniedHandler(securityErrorHandler);
                });
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain configure(HttpSecurity http,
                                         SecurityErrorHandler securityErrorHandler,
                                         AuthSuccessHandler authSuccessHandler,
                                         AuthFailHandler authFailHandler,
                                         AuthLogoutSuccessHandler authLogoutSuccessHandler,
                                         MemberService memberService,
                                         ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                         OAuth2SuccessHandler oAuth2SuccessHandler,
                                         OAuth2FailHandler oAuth2FailHandler,
                                         AuthService authService,
                                         @Value("${app.remember-me-key}") String rememberMeKey) throws Exception {

        // 로그인 한 번(잠금 확인 → 비밀번호 확인 → 성공·실패 기록)을 하나의 트랜잭션으로 묶는 인증 제공자 LoginAuthenticationProvider 는
        // 빈으로 등록돼 있어 Spring Security 가 인증 관리자에 자동으로 넣는다. 여기서 http.authenticationProvider 로 한 번 더 넣으면
        // 같은 제공자가 위·아래 두 인증 관리자에 모두 들어가 로그인 한 번에 실패가 두 번 기록된다 (LoginHttpTest 가 이를 확인한다)

        http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/", "/login", "/signup", "/password-reset", "/instructor/login", "/instructor/signup",
                            "/admin/login","/error", "/error/**", "/uploads/**", "/api/v1/auth/**", "/api/v1/agreements", "/api/v1/admin/auth/login").permitAll();      // ④ 누구나
                    // 화면에 쓰는 정적 파일(CSS·JS·이미지): 로그인 전 화면(/login, /signup)도 그려야 하므로 누구나 (#230)
                    auth.requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll();
                    // 간편 로그인(구글, #9): 인증 시작·제공자가 돌려보내는 주소, 처음 온 사람의 추가 정보 화면
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**", "/signup/social").permitAll();
                    // 서버 상태 확인(#84) : health·info 만 누구나. 나머지 actuator 주소는 웹에 열지 않아(application.yaml) 로그인해도 404
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
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
                // 로그인 상태 유지 (#367) : 로그인 요청에 remember-me=true 가 있으면 14일짜리 쿠키를 준다. 세션이 끝나도(브라우저 재시작·30분 만료)
                // 다음 요청에서 쿠키로 다시 로그인된다. 쿠키로 로그인할 때도 AuthService(UserDetailsService)를 거치므로 정지·탈퇴·잠금 계정은
                // 로그인되지 않고 쿠키가 지워진다. 비밀번호를 바꾸면 쿠키 서명이 맞지 않아 기존 쿠키는 쓸 수 없다
                .rememberMe(rememberMe -> {
                    rememberMe.key(rememberMeKey);                    // 쿠키 서명 키 : 환경변수 REMEMBER_ME_KEY (기본값 없음)
                    rememberMe.rememberMeServices(rememberMeServices(rememberMeKey, authService));
                })
                .logout(logout -> {
                    logout.logoutUrl("/api/v1/auth/logout");           // ⑧
                    logout.deleteCookies("JSESSIONID", REMEMBER_ME);   // 로그인 상태 유지 쿠키도 지운다 (#367)
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

        // 간편 로그인(구글, #9): GOOGLE_CLIENT_ID 와 GOOGLE_CLIENT_SECRET 이 모두 있어 등록 정보가 만들어졌을 때만 켠다. 하나라도 비면 서버는 그대로 뜨고 이 로그인만 없다.
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> {
                oauth.loginPage("/login");
                oauth.successHandler(oAuth2SuccessHandler);   // 가입한 사람은 로그인, 처음이면 추가 정보 화면으로
                oauth.failureHandler(oAuth2FailHandler);      // 실패는 로그인 화면으로 (?oauthError=OAUTH_FAILED)
            });
        }

        return http.build();
    }

    /**
     * 로그인 상태 유지 쿠키를 만들고 읽는 서비스 (#367). 쿠키는 HttpOnly(기본값)이고 SameSite=Lax 를 붙인다.
     * rememberMe 설정(DSL)에는 SameSite 를 정하는 메서드가 없어 서비스를 직접 만들어 넘긴다.
     */
    private static TokenBasedRememberMeServices rememberMeServices(String key, AuthService authService) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(key, authService);
        services.setParameter(REMEMBER_ME);                      // 요청 값 remember-me=true (on·yes·1 도 받는다)
        services.setCookieName(REMEMBER_ME);
        services.setTokenValiditySeconds(REMEMBER_ME_SECONDS);
        services.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
        return services;
    }
}
