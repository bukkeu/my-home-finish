package com.wanted.moneybeetle.domain.home.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : PM (#56, 통합 검색 화면)
*   화면 : GET /user/search?q=검색어 -> templates/home/search.html (공통 헤더의 검색창이 이 주소로 보낸다)
*   화면 틀만 서버에서 그리고, 검색 결과는 static/js/search.js 가 API 28(GET /api/v1/search)을 fetch 로 불러 채운다.
*   종목은 모의투자가 해금된 회원에게만 API 가 결과에 넣어 준다(INV-01), 그래서 이 화면은 레벨을 따로 검사하지 않는다.
*   layout(LayoutDTO) : 넣어 주는 곳이 아직 없어서 넘기지 않는다. 조각들이 null 이면 기본값으로 그린다.
*  */
@Controller
public class SearchPageController {

    @GetMapping("/user/search")
    public String searchPage() {
        return "home/search";
    }
}
