package com.wanted.moneybeetle.domain.operation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : DBA ② (관리자 운영 화면, 이슈 #61)
*   관리자 대시보드 화면(Thymeleaf 템플릿)을 주소에 연결한다. 화면 구현은 #61(FE), 데이터는 관리자 API(#33~#39)가 준다.
*   - GET /admin/dashboard : admin/dashboard (탭 틀). 탭은 ?view= 로 고르고 모르는 값은 대시보드로 보인다 (템플릿이 판단한다)
*   - 관리자만 들어온다 (SecurityConfig 의 /admin/** 규칙). 로그인 화면은 /admin/login 이다.
*  */
@Controller
public class AdminDashboardViewController {

    @GetMapping("/admin/dashboard")
    public String dashboard() {
        return "admin/dashboard";
    }
}
