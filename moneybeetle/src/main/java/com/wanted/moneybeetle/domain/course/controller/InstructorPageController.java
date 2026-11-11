package com.wanted.moneybeetle.domain.course.controller;

import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/* comment.
 *   담당 : DBA ① 신아림 (#59, 강사 로그인·가입·대시보드 화면)
 *   화면 : /instructor/login (auth/instructor-login), /instructor/signup (member/instructor-signup) - 누구나 (SecurityConfig)
 *          /instructor/dashboard?tab=dashboard|courses|qna|mypage (instructor/dashboard) - 승인된 강사(INSTRUCTOR)만
 *          /instructor/course-create[?courseId=] (instructor/course-create, #60) - 새 강의 만들기·내 강의 고치기. 승인된 강사만
 *   데이터는 화면 JS 가 API 로 받는다 (강사 가입 API 12, 내 강의 API 72, 강사 문의함 API 82, 문의 상세·답글 API 49·52)
 *   tab 은 사이드바(공통 sidebarFragment)의 강사 메뉴 주소와 같다. 모르는 값이면 dashboard
 *  */
@Controller
public class InstructorPageController {

    private static final Set<String> TABS = Set.of("dashboard", "courses", "qna", "mypage");

    @GetMapping("/instructor/login")
    public String loginPage() {
        return "auth/instructor-login";
    }

    @GetMapping("/instructor/signup")
    public String signupPage() {
        return "member/instructor-signup";
    }

    @GetMapping("/instructor/course-create")
    public String courseCreatePage() {
        return "instructor/course-create";
    }

    @GetMapping("/instructor/dashboard")
    public String dashboardPage(@RequestParam(required = false) String tab, Model model) {
        model.addAttribute("tab", tab != null && TABS.contains(tab) ? tab : "dashboard");
        return "instructor/dashboard";
    }
}
