package com.wanted.moneybeetle.support;

import com.wanted.moneybeetle.domain.auth.config.SecurityConfig;
import com.wanted.moneybeetle.domain.auth.handler.AuthSuccessHandler;
import com.wanted.moneybeetle.global.security.SecurityErrorHandler;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #161 실제 SecurityConfig 를 띄우는 테스트의 공통 설정 (DB 없이)
*   - 테스트마다 SecurityConfig 가 필요로 하는 빈을 하나씩 나열하던 것을 여기 한 곳에 모은다
*   - 로그인 핸들러는 domain.auth.handler 패키지를 통째로 스캔한다 → 핸들러가 새로 생겨도 테스트 파일은 그대로
*   - 쓰는 법 : @SpringBootTest(classes = {SecurityTestApp.class, 테스트에만 필요한 컨트롤러·서비스})
*     핸들러가 쓰는 서비스를 가짜로 두려면 테스트 클래스에 @MockSecurityServices 를 붙인다
*  */
@Configuration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@ComponentScan(basePackageClasses = AuthSuccessHandler.class)        // 로그인 성공·실패·로그아웃·관리자 로그인 핸들러
@Import({SecurityConfig.class, SecurityErrorHandler.class})
public class SecurityTestApp {
}
