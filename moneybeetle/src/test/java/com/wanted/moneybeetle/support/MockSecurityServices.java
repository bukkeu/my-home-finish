package com.wanted.moneybeetle.support;

import com.wanted.moneybeetle.domain.auth.model.service.AuthService;
import com.wanted.moneybeetle.domain.auth.model.service.LoginAuthenticationProvider;
import com.wanted.moneybeetle.domain.growth.model.service.AttendanceService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   #161 SecurityConfig·로그인 핸들러가 쓰는 서비스를 한 번에 가짜(mock)로 둔다
*   - 로그인 자체를 확인하지 않는 테스트(권한·레벨·예외 흐름)에서 쓴다. 로그인 흐름 테스트는 실제 서비스를 쓴다
*   - AttendanceService : 로그인 성공 때 출석 기록 (AuthSuccessHandler)
*   - AuthService, LoginAuthenticationProvider : 로그인 처리
*   - MemberService : 로그인해 둔 세션의 계정 상태 확인 (SecurityConfig 의 ActiveMemberFilter)
*     가짜는 기본으로 false 를 돌려주므로, AuthDetails 로 로그인한 요청을 보내는 테스트는
*     @Autowired MemberService 를 받아 isActiveMember(..) 를 true 로 정해 둔다
*   SecurityConfig 가 서비스를 더 필요로 하면 여기 types 에만 추가한다
*  */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@MockitoBean(types = {AttendanceService.class, AuthService.class, LoginAuthenticationProvider.class, MemberService.class})
public @interface MockSecurityServices {
}
