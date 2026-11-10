package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.auth.model.dto.AuthDetails;
import com.wanted.moneybeetle.domain.housing.model.service.HousingPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : SCM (모의 청약)
*   모의 청약 화면 (#58, 화면설계 UserDemoHousingSubscription)
*   - GET /user/demo-housing-subscription → templates/housing/housing.html
*   - 화면은 Lv.12 미만이어도 열린다(제도 소개·청약통장은 볼 수 있고, 레벨이 필요한 영역만 흐리게). 데이터 API 는 서버에서 레벨을 막는다
*   - Model : page (HousingPageDTO - 레벨·해금 레벨·잠금 여부·오늘)
*  */
@Controller
@RequiredArgsConstructor
public class HousingViewController {

    private final HousingPageService housingPageService;

    @GetMapping("/user/demo-housing-subscription")
    public String housingPage(@AuthenticationPrincipal AuthDetails auth, Model model) {
        model.addAttribute("page", housingPageService.getPage(auth.getMemberId()));
        return "housing/housing";
    }
}
