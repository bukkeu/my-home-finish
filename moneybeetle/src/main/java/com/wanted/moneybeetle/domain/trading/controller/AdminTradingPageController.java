package com.wanted.moneybeetle.domain.trading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : PM (모의투자)
*   관리자 모의투자 운영 화면 (#62). 화면 틀만 서버가 그리고, 시즌·가격·뉴스 등 데이터는
*   static/js/admin-trading.js 가 관리자 API(API 명세서 114~141)를 fetch 로 불러 채운다.
*   /admin/** 는 SecurityConfig 에서 ADMIN 만 들어올 수 있다.
*  */
@Controller
public class AdminTradingPageController {

    /* 화면 : 모의투자 운영 (화면설계 5.4.2) */
    @GetMapping("/admin/trading-management")
    public String tradingManagement() {
        return "admin/trading";
    }
}
