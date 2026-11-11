package com.wanted.moneybeetle.domain.trading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : PM (#57, 모의투자 화면)
*   화면 : GET /user/demo-trading -> templates/trading/trading.html
*   화면 틀만 서버에서 그리고, 시즌·시세·주문 같은 데이터는 static/js/trading.js 가 /api/v1/trading/** 를 fetch 로 부른다 (API 53~62).
*   Lv.8 해금 검사는 API 쪽(LevelCheckInterceptor, @RequireLevel)이 한다. 레벨이 모자라면 API 가 403 LEVEL_LOCKED 를 주고
*   화면이 잠금 안내로 바꾼다 (NFR-09).
*   layout(LayoutDTO) : 공통 헤더·사이드바가 쓰는 값. 넣어 주는 곳(HomeService 등)이 아직 없어서 넘기지 않는다.
*   조각들이 layout 이 null 이면 기본값으로 그려지므로 화면은 깨지지 않는다.
*  */
@Controller
public class TradingPageController {

    @GetMapping("/user/demo-trading")
    public String tradingPage() {
        return "trading/trading";
    }
}
