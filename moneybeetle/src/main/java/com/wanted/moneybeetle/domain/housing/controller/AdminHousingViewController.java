package com.wanted.moneybeetle.domain.housing.controller;

import com.wanted.moneybeetle.domain.housing.model.service.HousingPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : SCM (모의 청약)
*   관리자 모의 청약 운영 화면 (#63, 화면설계 AdminHousingManagement)
*   - GET /admin/housing-management → templates/admin/housing.html (관리자만, SecurityConfig 의 /admin/**)
*   - Model : today (서울 날짜. 산정은 마감 다음 날부터, 발표는 발표일부터 버튼을 켠다. 서버도 같은 기준으로 다시 막는다)
*   - 데이터는 static/js/admin-housing.js 가 운영 API(142~148)로 불러온다
*  */
@Controller
@RequiredArgsConstructor
public class AdminHousingViewController {

    private final HousingPageService housingPageService;

    @GetMapping("/admin/housing-management")
    public String housingManagementPage(Model model) {
        model.addAttribute("today", housingPageService.today());
        return "admin/housing";
    }
}
