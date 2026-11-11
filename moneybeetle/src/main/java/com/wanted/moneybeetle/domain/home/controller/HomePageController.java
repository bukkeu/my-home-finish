package com.wanted.moneybeetle.domain.home.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : DBA ② (홈 화면, 이슈 #248 · 화면은 #51)
*   서비스 소개 메인과 학습자 홈 화면을 주소에 연결한다. 화면이 쓰는 데이터는 화면 스크립트(home.js)가 API 19 등으로 불러온다.
*   - GET /          : index (누구나. SecurityConfig 에서 로그인 없이 열려 있다)
*   - GET /user/home : home/home (로그인한 회원. 헤더·메뉴가 쓰는 Model layout 은 LayoutInterceptor 가 넣는다)
*  */
@Controller
public class HomePageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/user/home")
    public String home() {
        return "home/home";
    }
}
