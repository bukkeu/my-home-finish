package com.wanted.moneybeetle.domain.inquiry.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
 *   담당 : DBA ① 신아림 (#55, 문의 게시판 화면)
 *   화면 : /user/inquiry (문의 게시판), /user/inquiry/{inquiryId} (답변 알림 링크 → 그 문의를 펼쳐서 연다)
 *   데이터는 화면이 문의 API(InquiryController, API 47~52)로 받는다. 문의 번호는 화면 JS 가 주소에서 읽는다
 *  */
@Controller
public class InquiryPageController {

    @GetMapping({"/user/inquiry", "/user/inquiry/{inquiryId}"})
    public String inquiryPage() {
        return "inquiry/inquiry";
    }
}
