package com.wanted.moneybeetle.domain.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* comment.
*   담당 : SCM 서수영 (#54, 마이페이지 틀 — 이재성에게서 넘겨받음, member 도메인 담당 김예린 동의 2026-11-11)
*   마이페이지 화면 (화면설계 UserMyPage)
*   - GET /user/my-page → templates/member/mypage.html (로그인 회원만, SecurityConfig 의 /user/**)
*   - 틀은 프로필 카드와 탭 전환만 맡고, 탭 내용은 각 담당의 조각(fragments/mypage/*)이 자기 API 로 불러온다
*   - 처음 열 탭은 주소의 ?tab= 또는 #탭이름 으로 고른다 (사이드바가 ?tab=wrong 처럼 연결). 화면 스크립트가 읽는다
*  */
@Controller
public class MyPageViewController {

    @GetMapping("/user/my-page")
    public String myPage() {
        return "member/mypage";
    }
}
